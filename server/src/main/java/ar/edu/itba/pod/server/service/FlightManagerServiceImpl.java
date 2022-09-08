package ar.edu.itba.pod.server.service;

import ar.edu.itba.pod.callbacks.NotificationHandler;
import ar.edu.itba.pod.models.*;
import ar.edu.itba.pod.interfaces.FlightManagerService;
import ar.edu.itba.pod.server.ServerStore;
import ar.edu.itba.pod.server.models.Flight;
import ar.edu.itba.pod.server.models.Plane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class FlightManagerServiceImpl implements FlightManagerService {

    private static final Logger logger = LoggerFactory.getLogger(FlightManagerServiceImpl.class);
    private final ServerStore store;

    final Lock modelsLock = new ReentrantLock();

    public FlightManagerServiceImpl(ServerStore store) {
        this.store = store;
    }

    @Override
    public void addPlaneModel(String model, Map<String, int[]> seatCategories) {
        modelsLock.lock();

        try {
            if (store.getPlaneModels().containsKey(model))
                throw new RuntimeException(); // TODO: nuestra excepcion

            store.getPlaneModels().put(model, new PlaneModel(model, seatCategories));
            logger.info("Added model");

            // TODO: preguntar si es mucho tiempo porque la alternativa seria lockear 2 veces
        } finally {
            modelsLock.unlock();
        }

    }

    @Override
    public void addFlight(String planeModel, String flightCode, String destination, List<Ticket> tickets) throws RemoteException {
        PlaneModel model;
        modelsLock.lock();
        try {
            model = Optional.of(store.getPlaneModels()
                    .get(planeModel)).orElseThrow(RuntimeException::new);
        } finally {
            modelsLock.unlock();
        }

        store.getFlightsLock().lock();
        try {
            if (store.getFlights().containsKey(flightCode))
                throw new RuntimeException();

            store.getFlights().put(flightCode, new Flight(new Plane(model), flightCode, destination, tickets));
            // TODO: preguntar si es mucho tiempo porque la alternativa seria lockear 2 veces
        } finally {
            store.getFlightsLock().unlock();
        }
    }

    @Override
    public FlightState getFlightState(String flightCode) throws RemoteException {
        FlightState flightState;
        store.getFlightsLock().lock();
        try {
            flightState = Optional.of(store.getFlights().get(flightCode))
                    .orElseThrow(RuntimeException::new).getState(); // TODO nuestra excepcion
        } finally {
            store.getFlightsLock().unlock();
        }

        return flightState;
    }

    @Override
    public void confirmFlight(String flightCode) throws RemoteException {
        changeFlightState(flightCode, FlightState.CONFIRMED);
    }

    @Override
    public void cancelFlight(String flightCode) throws RemoteException {
        changeFlightState(flightCode, FlightState.CANCELED);
    }

    private void changeFlightState(String flightCode, FlightState state) {
        Flight flight;
        store.getFlightsLock().lock();
        try {
            flight = Optional.ofNullable(store.getFlights().get(flightCode))
                    .filter(f -> f.getState().equals(FlightState.PENDING))
                    .orElseThrow(IllegalArgumentException::new); // TODO: custom exception
        } finally {
            store.getFlightsLock().unlock();
        }

        synchronized (flight) {
            flight.setState(state);
        }

        Map<String, List<NotificationHandler>> flightNotifications;
        store.getNotificationsLock().lock();
        flightNotifications = store.getNotifications().get(flightCode);
        store.getNotificationsLock().unlock();

        if (flightNotifications == null)
            return;

        synchronized (flightNotifications) { // TODO: preguntar si es excesivo
            flightNotifications.forEach((passenger, handlers) -> {
                synchronized (handlers) {
                    Ticket ticket = flight.getTickets().stream()
                            .filter(t -> t.getPassenger().equals(passenger)).findFirst()
                            .orElseThrow(IllegalArgumentException::new); // TODO check

                    for (NotificationHandler handler : handlers) {
                        store.submitNotificationTask(() -> {
                            try {
                                switch (flight.getState()) {
                                    case CONFIRMED:
                                        handler.notifyConfirmFlight(flightCode, flight.getDestination(),
                                                state, ticket.getCategory(), ticket.getRow(), ticket.getCol());
                                        break;
                                    case CANCELED:
                                        handler.notifyCancelFlight(flightCode, flight.getDestination(),
                                                state, ticket.getCategory(), ticket.getRow(), ticket.getCol());
                                        break;
                                }
                            } catch (RemoteException e) {
                                throw new RuntimeException(e); // TODO: excepcion propia
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    public void changeCancelledFlights() throws RemoteException { // TODO: modularizar
        store.getFlightsLock().lock();
        Map<FlightState, List<Flight>> partition = store.getFlights().values().stream()
                .filter(flight -> !flight.getState().equals(FlightState.CONFIRMED))
                .collect(Collectors.groupingBy(Flight::getState));
        store.getFlightsLock().unlock();

        List<Flight> cancelledFlights = partition.get(FlightState.CANCELED)
                .stream().sorted(Comparator.comparing(Flight::getCode)).collect(Collectors.toList());


        // TODO: Esto tiene que estar mucho mas sincronizado, me pueden confirmar un vuelo y despues no le puedo agregar pasajeros
//        List<Flight> pendingFlights = partition.get(FlightState.PENDING)
//                .stream().sorted(Comparator.comparing(Flight::getCode)).collect(Collectors.toList());

        for (Flight cancelledFlight : cancelledFlights) {
            // TODO: preguntar si hace falta sync con flights porque lo unico que cambiamos es el estado, no los tickets
            List<Ticket> tickets = cancelledFlight.getTickets().stream()
                    .sorted(Comparator.comparing(Ticket::getPassenger)).collect(Collectors.toList());

            for (Ticket ticket : tickets) {
                // TODO: no hace falta sync sobre el ticket porque son final
                String destination = ticket.getDestination();
                RowCategory category = ticket.getCategory();

                store.getFlightsLock().lock();
//                Flight newFlight = pendingFlights.stream()
//                        .filter(flight -> Objects.equals(flight.getDestination(), destination) &&
//                                hasAvailableSeats(flight, category))
//                        .min(Comparator.comparing(flight -> flight.getPlane().getAvailableByCategory(category)))
//                        .orElse(null);

                Comparator<Flight> seatCountComparator = Comparator.comparing(flight ->
                        flight.getPlane().getAvailableSeats()[flight.getPlane().getAvailableByCategory(category)]);

                Comparator<Flight> catComparator = Comparator.comparing(flight -> flight.getPlane().getAvailableByCategory(category));

                Flight newFlight = store.getFlights().values().stream()
                        .filter(flight -> flight.getState().equals(FlightState.PENDING) &&
                                Objects.equals(flight.getDestination(), destination) &&
                                flight.getPlane().getAvailableByCategory(category) != -1)
                        .min(catComparator.thenComparing(seatCountComparator.reversed()))
                        .orElse(null);

                if (newFlight == null) {
                    logger.info("No alternative flights found for passenger " + ticket.getPassenger());
                    store.getFlightsLock().unlock();
                    continue;
                }

//                newFlight.getPlane().getSeatLock().lock();
                store.getFlightsLock().unlock();

                synchronized(newFlight) {
                    newFlight.getTickets().add(ticket);
                }

                synchronized (cancelledFlight) {
                    cancelledFlight.removeTicket(ticket);
                }

                Map<String, List<NotificationHandler>> flightNotifications;
                store.getNotificationsLock().lock();
                flightNotifications = store.getNotifications().get(cancelledFlight.getCode());
                store.getNotificationsLock().unlock();

                if (flightNotifications == null)
                    continue;

                synchronized (flightNotifications) {
                    flightNotifications.forEach((passenger, handlers) -> {
                        synchronized (handlers) {
                            for (NotificationHandler handler : handlers) {
                                store.submitNotificationTask(() -> {
                                    try {
                                        handler.notifyChangeTicket(cancelledFlight.getCode(), cancelledFlight.getDestination(), newFlight.getCode());
                                    } catch (RemoteException e) {
                                        throw new RuntimeException(e); // TODO: excepcion propia
                                    }
                                });
                            }
                        }
                    });
                }
            }
        }
    }
}
