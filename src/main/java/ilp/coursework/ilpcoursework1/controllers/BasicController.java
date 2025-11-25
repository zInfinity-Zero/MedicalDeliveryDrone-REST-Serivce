package ilp.coursework.ilpcoursework1.controllers;


import ilp.coursework.ilpcoursework1.Drone.Drone;
import ilp.coursework.ilpcoursework1.PosandDis.RegionReq;
import ilp.coursework.ilpcoursework1.Util.DeliveryPathDTO;
import ilp.coursework.ilpcoursework1.Util.MedDispatchRec;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import ilp.coursework.ilpcoursework1.PosandDis.Distance;
import ilp.coursework.ilpcoursework1.PosandDis.Position;
import ilp.coursework.ilpcoursework1.PosandDis.NextPositionReq;
import ilp.coursework.ilpcoursework1.Service.Services;
import ilp.coursework.ilpcoursework1.Service.DroneService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class BasicController {

    private final Services s;
    private final DroneService dS;

    public BasicController(Services service, DroneService dS) {
        this.s = service;
        this.dS = dS;
    }

    @GetMapping("/uid")
    public String studentId() {
        return "s2483433";
    }

    @PostMapping("/distanceTo")
    public ResponseEntity<Double> distanceTo(@RequestBody Distance distance) {
        try {
            double answer = s.calculateDistance(distance);
            return ResponseEntity.ok(answer);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/isCloseTo")
    public ResponseEntity<Boolean> isCloseTo(@RequestBody Distance distance) {
        try {
            Boolean result = s.isCloseTo(distance);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }


    @PostMapping("/nextPosition")
    public ResponseEntity<Position> nextPosition(@RequestBody NextPositionReq req) {
        try {
            Position result = s.nextPosition(req.getStart(), req.getAngle());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/isInRegion")
    public ResponseEntity<Boolean> isInRegion(@RequestBody RegionReq req) {
        try {
            Boolean result = s.isInRegion(req.getPosition(), req.getRegion().getVertices());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    //cw2
    @GetMapping("/dronesWithCooling/{state}")
    public ResponseEntity<List<String>> dronesWithCooling(@PathVariable boolean state) {

        try {
            List<String> results = dS.getDronesWithCooling(state);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            List<String> error = new ArrayList<>();
            return ResponseEntity.ok(error);
        }
    }

    @GetMapping("/droneDetails/{id}")
    public ResponseEntity<?> droneDetails(@PathVariable String id) {

        try {
            Drone drone = dS.getDroneById(id);
            return ResponseEntity.ok(drone);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Drone not found");
        }
    }

    @GetMapping("/queryAsPath/{attribute}/{value}")
    public ResponseEntity<List<String>> queryAsPath(
            @PathVariable String attribute,
            @PathVariable String value) {


        try {
            List<String> result = dS.queryAsPath(attribute, value);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            List<String> error = new ArrayList<>();
            return ResponseEntity.ok(error);
        }
    }

    @PostMapping("/query")
    public ResponseEntity<List<String>> query(@RequestBody List<Map<String, String>> conditions) {

        try {
            List<String> result = dS.queryDrones(conditions);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            List<String> error = new ArrayList<>();
            return ResponseEntity.ok(error);
        }
    }

    @PostMapping("/queryAvailableDrones")
    public ResponseEntity<List<String>> queryAvailableDrones(
            @RequestBody List<MedDispatchRec> dispatches) {
        try {
            List<String> result = dS.queryAvailableDrones(dispatches);

            return ResponseEntity.ok(result);
        }
        catch(Exception e){
            List<String> error = new ArrayList<>();
            return ResponseEntity.ok(error);
        }
    }

    @PostMapping("/calcDeliveryPath")
    public ResponseEntity<DeliveryPathDTO.CalcResult> calcDeliveryPath(
            @RequestBody List<MedDispatchRec> dispatches) {
        try {
            DeliveryPathDTO.CalcResult result = dS.calcDeliveryPathCW2(dispatches);

            return ResponseEntity.ok(result);
        }
        catch (Exception e) {
            DeliveryPathDTO.CalcResult  error = new DeliveryPathDTO.CalcResult ();
            return ResponseEntity.ok(error);
        }
    }

    @PostMapping("/calcDeliveryPathAsGeoJson")
    public ResponseEntity<Map<String, Object>> calcDeliveryPathAsGeoJson(
            @RequestBody List<MedDispatchRec> dispatches) {
        try {
            Map<String, Object> geo = dS.calcDeliveryPathAsGeoJson(dispatches);
            return ResponseEntity.ok(geo);
        }
        catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            return ResponseEntity.ok(error);
        }
    }


    /// CW3
    @GetMapping("/test/battery-path")
    public ResponseEntity<String> testBatteryPath() {
        // Setup test drone
        return dS.testing();
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        // Setup test drone
        return dS.testSchedulerDebug();
    }

    @GetMapping("/testcharging")
    public ResponseEntity<String> testCharging() {
        // Setup test drone
        return dS.testChargingScenario();
    }

    @GetMapping("/testmultideliveries")
    public ResponseEntity<String> testMultiDeliveries() {
        // Setup test drone
        return dS.testMultipleDeliveriesWithCharging();
    }

    @GetMapping("/testmultidrones")
    public ResponseEntity<String> testMultiDrones() {
        // Setup test drone
        return dS.testTwoDronesMultipleDeliveries();
    }

    @GetMapping("/testnodrones")
    public ResponseEntity<String> tsetNoDrones() {
        // Setup test drone
        return dS.testNoAvailableDrones();
    }

}
