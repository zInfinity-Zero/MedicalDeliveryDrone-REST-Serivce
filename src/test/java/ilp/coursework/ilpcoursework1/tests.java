package ilp.coursework.ilpcoursework1;
import static java.lang.System.in;
import static org.junit.jupiter.api.Assertions.*;

import ilp.coursework.ilpcoursework1.CW3.BatteryModel;
import ilp.coursework.ilpcoursework1.CW3.BatteryService;
import ilp.coursework.ilpcoursework1.CW3.SchedulingService;
import ilp.coursework.ilpcoursework1.Drone.*;
import ilp.coursework.ilpcoursework1.PosandDis.Position;
import ilp.coursework.ilpcoursework1.Service.DroneService;
import ilp.coursework.ilpcoursework1.Service.Services;
import ilp.coursework.ilpcoursework1.Util.DeliveryPathDTO;
import ilp.coursework.ilpcoursework1.Util.GeoValidator;
import ilp.coursework.ilpcoursework1.Util.MedDispatchRec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class tests {
    private DroneService service;

    //Stubs on ilpservice as it queries external state
    class FakeILPService extends ILPService {

        FakeILPService() {
            super(null); // WebClient not needed
        }

        @Override
        public List<ServicePoint> fetchServicePoints() {
            ServicePoint sp = new ServicePoint();
            sp.setId(1);
            sp.setRechargeRate(0);
            sp.setMaxConcurrentSlots(2);
            sp.setLocation(new ServicePoint.Location(0.0, 0.0));
            return List.of(sp);
        }

        @Override
        public List<RestrictedZone> fetchRestrictedAreas() {
            return List.of(); // empty = no restrictions
        }

        @Override
        public List<Drone> fetchDrones() {
            return List.of(); // not used in your unit tests
        }
//        @Override
//        public List<ServicePointDroneListDTO> fetchDronesForServicePoints() {
//            return List.of(TestFixtures.mockServicePointDroneMapping());
//        }
    }

    @BeforeEach
    void setUp() {
        ILPService ilp = new FakeILPService();
        Services s = new Services();
        BatteryService batteryService = new BatteryService();
        SchedulingService schedulingService = new SchedulingService();

        service = new DroneService(ilp, s, batteryService, schedulingService);
    }



    public static class TestFixtures {//fake models

        static Drone basicDroneWithBattery(double charge) {
            BatteryModel battery = new BatteryModel();
            battery.setCapacity(100.0);
            battery.setCurrentCharge(charge);
            battery.setDegradationFactor(1.0);

            Capability cap = new Capability();
            cap.setBattery(battery);
            cap.setCapacity(10.0);
            cap.setCruiseSpeed(10.0);

            Drone d = new Drone();
            d.setId("D1");
            d.setCapability(cap);
            d.setServicePointId(1);

            return d;
        }

        static Drone basicAvailableDrone() {
            Drone d = basicDroneWithBattery(100.0);

            Availability av = new Availability();
            av.setDayOfWeek(LocalDate.now().getDayOfWeek());
            av.setFrom(LocalTime.of(12, 0));
            av.setUntil(LocalTime.of(23, 0));
            d.setAvailability(List.of(av));
            Capability cap = new Capability();
            cap.setCapacity(10.0);
            cap.setCooling(true);
            cap.setHeating(true);
            BatteryModel bm = new BatteryModel();
            bm.setCapacity(100.0);
            bm.setBaseConsumptionPerStep(0.05);
            bm.setConsumptionPayloadFactor(0.2);
            bm.setDegradationFactor(0.1);
            bm.setCurrentCharge(100.0);
            cap.setBattery(bm);
            cap.setCruiseSpeed(0.000015);
            d.setCapability(cap);


            return d;
        }
//        public static ILPService.ServicePointDroneListDTO mockServicePointDroneMapping() {
//            ILPService.DroneAvailabilityDTO avail = new ILPService.DroneAvailabilityDTO();
//            // Uses the current day to ensure the scheduler finds it active during the test
//            avail.setDayOfWeek(LocalDate.now().getDayOfWeek().toString());
//
//            avail.setFrom("01:00");
//            avail.setUntil("23:01");
//
//            ILPService.ServicePointDroneDTO d = new ILPService.ServicePointDroneDTO();
//            d.setId("D1"); // Must match the ID in basicDroneWithBattery
//            d.setAvailability(List.of(avail));
//
//            ILPService.ServicePointDroneListDTO listDTO = new ILPService.ServicePointDroneListDTO();
//            listDTO.setServicePointId(1);
//            listDTO.setDrones(List.of(d));
//
//            return listDTO;
//        }
        static Drone droneUnavailableOn(DayOfWeek day) {
            Drone d = basicDroneWithBattery(100.0);

            Availability av = new Availability();
            av.setDayOfWeek(day.plus(1)); // deliberately unavailable
            d.setAvailability(List.of(av));

            return d;
        }

        static MedDispatchRec standardDeliveryOn(DayOfWeek day) {
            MedDispatchRec rec = new MedDispatchRec();
            rec.setId(1);
            rec.setDate(LocalDate.now());
            rec.setTime(LocalTime.NOON);
            MedDispatchRec.Requirements req = new MedDispatchRec.Requirements();
            req.setCapacity(5.0);
            req.setCooling(false);
            req.setHeating(false);
            req.setMaxCost(1000.0);
            rec.setRequirements(req);
            MedDispatchRec.Delivery del = new MedDispatchRec.Delivery();
            del.setLng(0.01);
            del.setLat(0);
            rec.setDelivery(del);
            return rec;
        }

        static MedDispatchRec urgentDelivery() {
            MedDispatchRec rec = standardDeliveryOn(LocalDate.now().getDayOfWeek());

            rec.setImportant(true);
            rec.setId(2);
            return rec;
        }

        static MedDispatchRec normalDelivery() {
            MedDispatchRec rec = standardDeliveryOn(LocalDate.now().getDayOfWeek());

            rec.setImportant(false);
            rec.setId(3);
            return rec;


        }
    }


    @Test
    void pathFailsWhenBatteryInsufficient() {

        DeliveryPathDTO.LngLat start =
                new DeliveryPathDTO.LngLat(0.0, 0.0);
        DeliveryPathDTO.LngLat goal =
                new DeliveryPathDTO.LngLat(1, 0.0); // far enough

        Drone drone = TestFixtures.basicDroneWithBattery(1.0);

        DroneService.PathWithBatteryResult result =
                service.aStarPathWithBattery(
                        start,
                        goal,
                        drone,
                        1.0,      // very low charge
                        0.0,
                        List.of()
                );

        assertTrue(
                result == null || result.batteryRemaining >= 0,
                "Pathfinding must not return a path with negative battery"
        );
    }

    @Test
    void unavailableDroneIsNotAssignedDelivery() {

        Drone unavailableDrone =
                TestFixtures.droneUnavailableOn(DayOfWeek.MONDAY);

        MedDispatchRec delivery =
                TestFixtures.standardDeliveryOn(DayOfWeek.MONDAY);

        DroneService.EnhancedPlanResult result =
                service.calcDeliveryPathWithScheduling(
                        List.of(delivery),
                        List.of(unavailableDrone)
                );

        assertTrue(result.missions.isEmpty(),
                "Unavailable drone should not receive assignments");
    }

    @Test
    void canHandleChecksAllCapabilities() {
        // 1. Setup a standard delivery requiring cooling and specific capacity
        MedDispatchRec coolingReq = TestFixtures.standardDeliveryOn(DayOfWeek.MONDAY);
        coolingReq.getRequirements().setCooling(true);
        coolingReq.getRequirements().setCapacity(5.0); // Keep within reasonable limits

        // 2. Scenario A: Drone has capacity but NO cooling (Negative Test)
        Drone noCoolingDrone = TestFixtures.basicAvailableDrone();
        noCoolingDrone.getCapability().setCooling(false);
        noCoolingDrone.getCapability().setCapacity(10.0);
        // Ensure battery is 100% to isolate the capability failure
        noCoolingDrone.getCapability().getBattery().setCurrentCharge(100.0);

        DroneService.EnhancedPlanResult resultA =
                service.calcDeliveryPathWithScheduling(List.of(coolingReq), List.of(noCoolingDrone));
        assertTrue(resultA.missions.isEmpty(), "Should reject drone missing cooling capability");

        // 3. Scenario B: Drone matches ALL (Positive Test)
        // FIX: Re-initialize to ensure fresh state and high battery
        Drone perfectDrone = TestFixtures.basicAvailableDrone();
        perfectDrone.getCapability().setCooling(true);
        perfectDrone.getCapability().setCapacity(10.0);
        perfectDrone.getCapability().getBattery().setCurrentCharge(100.0);

        DroneService.EnhancedPlanResult resultB =
                service.calcDeliveryPathWithScheduling(List.of(coolingReq), List.of(perfectDrone));

        assertFalse(resultB.missions.isEmpty(), "Should assign delivery when all capabilities match");
    }




}
