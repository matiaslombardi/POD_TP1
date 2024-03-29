package ar.edu.itba.pod.service;

import ar.edu.itba.pod.assets.TestConstants;
import ar.edu.itba.pod.models.AlternativeFlightResponse;
import ar.edu.itba.pod.models.RowCategory;
import ar.edu.itba.pod.models.exceptions.flightExceptions.IllegalFlightStateException;
import ar.edu.itba.pod.models.exceptions.seatExceptions.SeatAlreadyTakenException;
import ar.edu.itba.pod.server.utils.ServerStore;
import ar.edu.itba.pod.server.service.FlightManagerServiceImpl;
import ar.edu.itba.pod.server.service.SeatManagerServiceImpl;
import org.junit.Test;

import java.rmi.RemoteException;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SeatManagerServiceImplTest {

    private final ServerStore store = new ServerStore();
    private final FlightManagerServiceImpl flightManagerService = new FlightManagerServiceImpl(store);
    private final SeatManagerServiceImpl seatManagerService = new SeatManagerServiceImpl(store);

    @Test
    public void testIsSeatAvailableTrue() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.SEAT_CATEGORIES);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_1);
        boolean actualValue = seatManagerService.isAvailable(TestConstants.FLIGHT_CODE_1, TestConstants.ROW_1, TestConstants.SEAT_1);

        assertTrue(actualValue);
    }

    @Test
    public void testIsSeatAvailableFalse() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.EMPTY_SEAT_CATEGORIES);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_1);
        seatManagerService.assign(TestConstants.FLIGHT_CODE_1, TestConstants.PASSENGER_2, TestConstants.ROW_1, TestConstants.SEAT_1);

        boolean actualValue = seatManagerService.isAvailable(TestConstants.FLIGHT_CODE_1, TestConstants.ROW_1, TestConstants.SEAT_1);

        assertFalse(actualValue);
    }

    @Test
    public void testAssignPassengerWithAvailableSeats() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.EMPTY_SEAT_CATEGORIES);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_1);
        seatManagerService.assign(TestConstants.FLIGHT_CODE_1, TestConstants.PASSENGER_3, TestConstants.ROW_2, TestConstants.SEAT_1);

        assertEquals(0, store.getPendingFlights().get(TestConstants.FLIGHT_CODE_1).getAvailableByCategory(RowCategory.ECONOMY));
    }

    @Test(expected = IllegalFlightStateException.class)
    public void testAssignPassengerWithInvalidFlightState() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.SEAT_CATEGORIES);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_1);
        flightManagerService.cancelFlight(TestConstants.FLIGHT_CODE_1);
        seatManagerService.assign(TestConstants.FLIGHT_CODE_1, TestConstants.PASSENGER_1, TestConstants.ROW_1, TestConstants.SEAT_1);
    }

    @Test(expected = SeatAlreadyTakenException.class)
    public void testAssignPassengerWithNoAvailableSeats() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_3, TestConstants.EMPTY_SEAT_CATEGORIES);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_3, TestConstants.FLIGHT_CODE_3, TestConstants.DESTINATION_2, TestConstants.TICKETS_5);
        seatManagerService.assign(TestConstants.FLIGHT_CODE_3, TestConstants.PASSENGER_1, TestConstants.ROW_1, TestConstants.SEAT_1);
        seatManagerService.assign(TestConstants.FLIGHT_CODE_3, TestConstants.PASSENGER_3, TestConstants.ROW_1, TestConstants.SEAT_1);
    }

    @Test
    public void testChangeSeatSuccessfully() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.SEAT_CATEGORIES);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_1);

        seatManagerService.assign(TestConstants.FLIGHT_CODE_1, TestConstants.PASSENGER_4, TestConstants.ROW_5, TestConstants.SEAT_1);
        seatManagerService.changeSeat(TestConstants.FLIGHT_CODE_1, TestConstants.PASSENGER_4, TestConstants.ROW_5, TestConstants.SEAT_2);

        assertTrue(seatManagerService.isAvailable(TestConstants.FLIGHT_CODE_1, TestConstants.ROW_5, TestConstants.SEAT_1));
        assertFalse(seatManagerService.isAvailable(TestConstants.FLIGHT_CODE_1, TestConstants.ROW_5, TestConstants.SEAT_2));
    }

    @Test
    public void testListAlternativeFlightsSuccessfully() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.SEAT_CATEGORIES);
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_2, TestConstants.SEAT_CATEGORIES);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_3);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_2, TestConstants.FLIGHT_CODE_2, TestConstants.DESTINATION_1, TestConstants.TICKETS_4);
        flightManagerService.cancelFlight(TestConstants.FLIGHT_CODE_1);

        List<AlternativeFlightResponse> alternativeFlights = seatManagerService.listAlternativeFlights(TestConstants.FLIGHT_CODE_1, TestConstants.PASSENGER_1);

        assertEquals(1, alternativeFlights.size());
        assertEquals(TestConstants.FLIGHT_CODE_2, alternativeFlights.get(0).getFlightCode());
    }

    @Test(expected = IllegalFlightStateException.class)
    public void testListAlternativeFlightsWithConfirmedFlight() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.SEAT_CATEGORIES);
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_2, TestConstants.SEAT_CATEGORIES);

        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_3);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_2, TestConstants.FLIGHT_CODE_2, TestConstants.DESTINATION_1, TestConstants.TICKETS_4);

        flightManagerService.confirmFlight(TestConstants.FLIGHT_CODE_1);

        seatManagerService.listAlternativeFlights(TestConstants.FLIGHT_CODE_1, TestConstants.PASSENGER_1);
    }

    @Test
    public void testChangeFlightSuccessfully() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.SEAT_CATEGORIES);
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_2, TestConstants.SEAT_CATEGORIES);

        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_3);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_2, TestConstants.FLIGHT_CODE_2, TestConstants.DESTINATION_1, TestConstants.TICKETS_4);

        flightManagerService.cancelFlight(TestConstants.FLIGHT_CODE_1);

        seatManagerService.changeFlight(TestConstants.PASSENGER_1, TestConstants.FLIGHT_CODE_1, TestConstants.FLIGHT_CODE_2);

        assertEquals(TestConstants.PASSENGER_1, store.getFlight(TestConstants.FLIGHT_CODE_2).getTicket(TestConstants.PASSENGER_1).getPassenger());
    }

    @Test(expected = IllegalFlightStateException.class)
    public void testChangeFlightWithOldFlightConfirmed() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.SEAT_CATEGORIES);
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_2, TestConstants.SEAT_CATEGORIES);

        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_3);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_2, TestConstants.FLIGHT_CODE_2, TestConstants.DESTINATION_1, TestConstants.TICKETS_4);

        flightManagerService.confirmFlight(TestConstants.FLIGHT_CODE_1);

        seatManagerService.changeFlight(TestConstants.PASSENGER_2, TestConstants.FLIGHT_CODE_1, TestConstants.FLIGHT_CODE_2);
    }

    @Test(expected = IllegalFlightStateException.class)
    public void testChangeFlightWithNewFlightConfirmed() throws RemoteException {
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_1, TestConstants.SEAT_CATEGORIES);
        flightManagerService.addPlaneModel(TestConstants.PLANE_MODEL_STR_2, TestConstants.SEAT_CATEGORIES);

        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_1, TestConstants.FLIGHT_CODE_1, TestConstants.DESTINATION_1, TestConstants.TICKETS_3);
        flightManagerService.addFlight(TestConstants.PLANE_MODEL_STR_2, TestConstants.FLIGHT_CODE_2, TestConstants.DESTINATION_1, TestConstants.TICKETS_4);

        flightManagerService.confirmFlight(TestConstants.FLIGHT_CODE_2);

        seatManagerService.changeFlight(TestConstants.PASSENGER_2, TestConstants.FLIGHT_CODE_1, TestConstants.FLIGHT_CODE_2);
    }
}
