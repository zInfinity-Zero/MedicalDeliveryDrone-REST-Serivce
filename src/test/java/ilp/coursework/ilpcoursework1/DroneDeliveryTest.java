package ilp.coursework.ilpcoursework1;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import ilp.coursework.ilpcoursework1.CW3.BatteryModel;
import ilp.coursework.ilpcoursework1.CW3.BatteryService;
import ilp.coursework.ilpcoursework1.CW3.SchedulingService;
import ilp.coursework.ilpcoursework1.Drone.*;
import ilp.coursework.ilpcoursework1.PosandDis.Position;
import ilp.coursework.ilpcoursework1.Service.DroneService;
import ilp.coursework.ilpcoursework1.Service.Services;
import ilp.coursework.ilpcoursework1.Util.DeliveryPathDTO;
import ilp.coursework.ilpcoursework1.Util.MedDispatchRec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
class DroneDeliveryTest {
    private DroneService droneService;
    private ILPService mockIlp;
    private BatteryService batteryService;
    private SchedulingService schedulingService;
    private Services geoServices;

    @BeforeEach
    void setUp() {
        mockIlp = mock(ILPService.class);
        batteryService = new BatteryService();
        geoServices = new Services();
        // We use the real scheduling service to test prioritization logic
        schedulingService = new SchedulingService();
        droneService = new DroneService(mockIlp, geoServices, batteryService, schedulingService);
    }
    @Test
    void urgentDeliveryIsPrioritisedOverNormal() {
        // 1. Setup two drones with different speeds
        Drone fastDrone = createTestDrone("FAST-1", 0.0005, 0.2); // High speed, high drain
        Drone efficientDrone = createTestDrone("EFF-1", 0.0001, 0.1); // Slow, low drain

        when(mockIlp.fetchServicePoints()).thenReturn(List.of(createTestSP(1)));

        // 2. Setup two dispatches: one urgent, one normal
        MedDispatchRec normal = createDispatch(101, false);
        MedDispatchRec urgent = createDispatch(102, true);

        // 3. Execute scheduling
        DroneService.EnhancedPlanResult result = droneService.calcDeliveryPathWithScheduling(
                List.of(normal, urgent),
                List.of(fastDrone, efficientDrone)
        );
        System.out.println(fastDrone.getServicePointId());
        // 4. Assertions: Check assignment logic
        // Important: Pezze & Young emphasize "Correctness" relative to spec.
        // The urgent delivery should be assigned to the 'Fast' drone.
        Optional<DroneService.EnhancedDeliveryLeg> urgentLeg = result.missions.stream()
                .flatMap(m -> m.legs.stream())
                .filter(l -> l.deliveryId == 102)
                .findFirst();

        assertTrue(urgentLeg.isPresent(), "Urgent delivery should be assigned");
        // Verify the urgent mission uses the faster drone
        String assignedDroneId = result.missions.stream()
                .filter(m -> m.legs.stream().anyMatch(l -> l.deliveryId == 102))
                .findFirst().get().droneId;

        assertEquals("FAST-1", assignedDroneId, "Urgent delivery should prefer the faster drone");
    }

    /**
     * Test for LO1/LO3: Safety Violation (Restricted Zones)
     */
    @Test
    void pathMustNotEnterRestrictedZone() {
        DeliveryPathDTO.LngLat start = new DeliveryPathDTO.LngLat(-3.18, 55.94);
        DeliveryPathDTO.LngLat goal = new DeliveryPathDTO.LngLat(-3.19, 55.95);

        // Create a restricted zone directly between start and goal
        RestrictedZone wall = new RestrictedZone();
        wall.setVertices(List.of(
                new Position(-3.185, 55.945),
                new Position(-3.185, 55.946),
                new Position(-3.186, 55.946),
                new Position(-3.186, 55.945)
        ));

        List<DeliveryPathDTO.LngLat> path = droneService.aStarPath(start, goal, List.of(wall));

        assertNotNull(path, "Pathfinder should find a way around");
        for (DeliveryPathDTO.LngLat point : path) {
            assertFalse(geoServices.isInRegion(new Position(point.getLng(), point.getLat()), wall.getVertices()),
                    "Path point " + point + " entered a restricted zone!");
        }
    }

    // Helper Methods for Scaffolding
    private Drone createTestDrone(String id, double speed, double drain) {
        Drone d = new Drone();
        d.setId(id);
        Capability cap = new Capability();
        cap.setCruiseSpeed(speed);
        cap.setCapacity(100.0);
        BatteryModel bm = new BatteryModel();
        bm.setCapacity(100.0);
        bm.setBaseConsumptionPerStep(drain);
        cap.setBattery(bm);
        d.setCapability(cap);
        d.setServicePointId(1);
        // Setup Wednesday availability
        Availability av = new Availability();
        av.setDayOfWeek(DayOfWeek.WEDNESDAY);
        av.setFrom(LocalTime.of(8,0));
        av.setUntil(LocalTime.of(18,0));
        d.setAvailability(List.of(av));
        return d;
    }

    private MedDispatchRec createDispatch(int id, boolean important) {
        MedDispatchRec r = new MedDispatchRec();
        r.setId(id);
        r.setImportant(important);
        r.setDate(LocalDate.of(2026, 1, 14)); // A Wednesday
        r.setTime(LocalTime.of(10, 0));
        MedDispatchRec.Requirements req = new MedDispatchRec.Requirements();
        req.setCapacity(1.0);
        r.setRequirements(req);
        MedDispatchRec.Delivery del = new MedDispatchRec.Delivery();
        del.setLng(-3.187); del.setLat(55.945);
        r.setDelivery(del);
        return r;
    }

    private ServicePoint createTestSP(int id) {
        ServicePoint sp = new ServicePoint();
        sp.setId(id);
        sp.setLocation(new ServicePoint.Location(-3.18, 55.94));
        sp.setRechargeRate(10.0);
        return sp;
    }
}