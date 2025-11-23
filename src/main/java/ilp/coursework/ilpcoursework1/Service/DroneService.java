package ilp.coursework.ilpcoursework1.Service;

import ilp.coursework.ilpcoursework1.CW3.BatteryModel;
import ilp.coursework.ilpcoursework1.CW3.BatteryService;
import ilp.coursework.ilpcoursework1.CW3.SchedulingService;
import ilp.coursework.ilpcoursework1.Drone.*;
import ilp.coursework.ilpcoursework1.ILPService;
import ilp.coursework.ilpcoursework1.PosandDis.Position;
import ilp.coursework.ilpcoursework1.Util.DeliveryPathDTO;
import ilp.coursework.ilpcoursework1.Util.MedDispatchRec;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DroneService {

    private final ILPService ilp;
    private final Services s;
    private final BatteryService batteryService;

    private final SchedulingService schedulingService;

    private static final double STEP = 0.00015;
    private static final double EPS = 1e-12;
    private static final double[] ALLOWED_ANGLES;

    static {
        ALLOWED_ANGLES = new double[16];
        for (int i = 0; i < 16; i++) ALLOWED_ANGLES[i] = i * 22.5;
    }

    public DroneService(ILPService ilpService, Services s, BatteryService batteryService, SchedulingService schedulingService) {
        this.ilp = ilpService;
        this.s = s;
        this.batteryService = batteryService;
        this.schedulingService = schedulingService;
    }

    // fetch each time
    public List<Drone> drones() {
        return ilp.fetchDrones();
    }

//    private List<Availability> convertAvailability(List<ILPService.DroneAvailabilityDTO> dtoList) {
//        if (dtoList == null) {
//            return new ArrayList<>();
//        }
//
//        return dtoList.stream()
//                .map(dto -> {
//                    Availability availability = new Availability();
//
//                    // Convert String "MONDAY" to DayOfWeek.MONDAY
//                    availability.setDayOfWeek(DayOfWeek.valueOf(dto.getDayOfWeek()));
//
//                    // Convert String "00:00:00" to a LocalTime object
//                    availability.setFrom(LocalTime.parse(dto.getFrom()));
//                    availability.setUntil(LocalTime.parse(dto.getUntil()));
//
//                    return availability;
//                })
//                .collect(Collectors.toList());
//    }
    public List<ServicePoint> fetchServicePoints() {
        return ilp.fetchServicePoints();
    }

    public List<RestrictedZone> fetchRestrictedAreas() {
        return ilp.fetchRestrictedAreas();
    }


    ///////////////////////
    public List<String> getDronesWithCooling(boolean cooling) {
        return drones().stream()
                .filter(d -> d.getCapability().isCooling() == cooling)
                .map(Drone::getId)
                .collect(Collectors.toList());
    }

    public  Drone getDroneById(String id) {
        Drone getDrone = drones().stream()
                .filter(d -> Objects.equals(d.getId(), id))
                .findFirst()
                .orElse(null);
        if(getDrone==null){
            throw new IllegalArgumentException("Invalid id");
        }
        return getDrone;
    }


    public Object getAttributeValue(Drone drone, String attribute) {

        switch (attribute) {
            case "id":
                return drone.getId();
            case "name":
                return drone.getName();
        }

        // Handle attributes inside Capability
        Capability c = drone.getCapability();
        if (c == null) return null;

        return switch (attribute) {
            case "capacity" -> c.getCapacity();
            case "cooling" -> c.isCooling();
            case "heating" -> c.isHeating();
            case "maxMoves" -> c.getMaxMoves();
            case "costPerMove" -> c.getCostPerMove();
            case "costInitial" -> c.getCostInitial();
            case "costFinal" -> c.getCostFinal();
            default -> null;
        };

    }
    public boolean attributeMatches(Drone drone, String attribute, String value) {
        Object realValue = getAttributeValue(drone, attribute);

        if (realValue == null)
            return false;

        if (realValue instanceof Number) {
            try {
                double expected = Double.parseDouble(value);
                double actual   = Double.parseDouble(realValue.toString());
                return actual == expected;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return realValue.toString().equalsIgnoreCase(value);
    }

    public List<String> queryAsPath(String attribute, String value) {

        return drones().stream()
                .filter(drone -> attributeMatches(drone, attribute, value))
                .map(Drone::getId)
                .collect(Collectors.toList());
    }


    public boolean matchesAll(Drone drone, List<Map<String, String>> conditions) {

        for (Map<String, String> cond : conditions) {
            String attribute = cond.get("attribute");
            String operator  = cond.get("operator");
            String value     = cond.get("value");

            Object actual = getAttributeValue(drone, attribute);
            if (actual == null) return false;

            if (!evaluate(actual, operator, value))
                return false;
        }

        return true;
    }

    private boolean evaluate(Object actualValue, String op, String value) {

        if (actualValue instanceof Number) {
            double left = Double.parseDouble(actualValue.toString());
            double right = Double.parseDouble(value);

            return switch (op) {
                case "="  -> left == right;
                case "!=" -> left != right;
                case "<"  -> left < right;
                case ">"  -> left > right;
                default   -> false;
            };
        }

        // Non-numerical
        return op.equals("=")
                && actualValue.toString().equalsIgnoreCase(value);
    }

    public List<String> queryDrones(List<Map<String, String>> conditions) {
        return drones().stream()
                .filter(d -> matchesAll(d, conditions))
                .map(Drone::getId)
                .toList();
    }




    ///
    public List<Drone> loadMergedDrones() {
        List<Drone> drones = ilp.fetchDrones();

        List<ILPService.ServicePointDroneListDTO> spInfo = ilp.fetchDronesForServicePoints();



        Map<String, Drone> byId = drones.stream()

                .collect(Collectors.toMap(Drone::getId, d -> d));



        for (ILPService.ServicePointDroneListDTO sp : spInfo) {

            int spId = sp.getServicePointId();

            for (ILPService.ServicePointDroneDTO d : sp.getDrones()) {

                Drone drone = byId.get(String.valueOf(d.getId()));
                if (drone == null) {
//                    System.out.println("WARNING: Drone ID from service-points NOT FOUND in drones(): " + d.getId());
                    continue;
                }
                drone.setServicePointId(spId);
                List<Availability> converted = Collections.emptyList();

                if (d.getAvailability() != null) {

                    converted = d.getAvailability().stream()

                            .map(a -> {

                                Availability av = new Availability();

                                av.setDayOfWeek(DayOfWeek.valueOf(a.getDayOfWeek()));

                                av.setFrom(safeParseTime(a.getFrom()));

                                av.setUntil(safeParseTime(a.getUntil()));

                                return av;

                            })

                            .toList();

                }



                drone.setAvailability(converted);

            }

        }
        //addingmytestdrone
        Drone d = new Drone();
        d.setId("TEST-1");

        Capability cap = new Capability();
        cap.setCapacity(10.0);
        cap.setCooling(true);
        cap.setHeating(true);

        BatteryModel bm = new BatteryModel();
        bm.setCapacity(100.0);
        bm.setBaseConsumptionPerStep(0.5);
        bm.setConsumptionPayloadFactor(0.2);
        bm.setDegradationFactor(0.1);
        bm.setCurrentCharge(10.0);

        cap.setBattery(bm);
        cap.setCruiseSpeed(0.00015);     // lnglat speed

        d.setCapability(cap);
        d.setServicePointId(1);       // Attach to SP 1 (Appleton Tower)

        // Add availability
        Availability av = new Availability();
        av.setDayOfWeek(DayOfWeek.WEDNESDAY);
        av.setFrom(LocalTime.of(9, 0));
        av.setUntil(LocalTime.of(17, 0));
        d.setAvailability(List.of(av));

        // Add test drone to list
        drones.add(d);


        System.out.println("=== MERGED DRONES ===");
        for (Drone dr : drones) {
            System.out.println(
                    "Drone " + dr.getId() +
                            " SP=" + dr.getServicePointId() +
                            " avail=" + dr.getAvailability()
            );
        }

        return drones;

    }

    private LocalTime safeParseTime(String t) {
        try { return LocalTime.parse(t); }
        catch (Exception e) {
            if (t.length() == 5) return LocalTime.parse(t + ":00");
            throw e;
        }
    }


    private boolean isAvailable(Drone drone, LocalDate date, LocalTime time) {
        for (Availability av : drone.getAvailability()) {
            if (av.getDayOfWeek() == date.getDayOfWeek()) {
                if (!time.isBefore(av.getFrom()) && !time.isAfter(av.getUntil())) {
                    return true;
                }
            }
        }
        if (drone.getAvailability() == null) return false;
        return false;
    }


//    public List<String> queryAvailableDrones(List<MedDispatchRec> dispatches) {
//
//        return loadMergedDrones().stream()
//                .filter(drone -> dispatches.stream().allMatch(rec -> canHandle(drone, rec)))
//                .map(Drone::getId)
//                .toList();
//    }
    public List<String> queryAvailableDrones(List<MedDispatchRec> dispatches) {

        return loadMergedDrones().stream()
                .filter(drone -> canHandleMultiLoad(drone, dispatches))
                .map(Drone::getId)
                .toList();
    }

    private Map<Integer, ServicePoint> loadServicePointsById() {
        return ilp.fetchServicePoints().stream()
                .collect(Collectors.toMap(ServicePoint::getId, sp -> sp));
    }
    private boolean canHandleMultiLoad(Drone d, List<MedDispatchRec> recs) {

        Capability c = d.getCapability();

        // 1) Availability check: drone must be available for ALL dispatches
        for (MedDispatchRec rec : recs) {
            if (!isAvailable(d, rec.getDate(), rec.getTime())) {
                System.out.println(d.getId()+"pnot assed ava check");

                return false;
            }
        }

        // 2) Combined capacity check
        double totalRequiredCapacity = 0;
        for (MedDispatchRec rec : recs) {
            if (rec.getRequirements().getCapacity() != null) {
                totalRequiredCapacity += rec.getRequirements().getCapacity();

            }
        }
        if (totalRequiredCapacity > c.getCapacity()) {
            System.out.println(d.getId()+"not passed cap check");
            return false;

        }

        // 3) Cooling / Heating requirements
        boolean needCooling = recs.stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.getRequirements().getCooling()));
        if (needCooling && !c.isCooling()) {
            System.out.println(d.getId()+"not passed cool check");
            return false;}

        boolean needHeating = recs.stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.getRequirements().getHeating()));
        if (needHeating && !c.isHeating()) {
            System.out.println(d.getId()+"not passed heat check");
            return false;}

        // 4) Fetch the drone's service point (from drone.servicePointId)
        ServicePoint sp = loadServicePointsById().get(d.getServicePointId());
        if (sp == null) return false;

        DeliveryPathDTO.LngLat spLoc = new DeliveryPathDTO.LngLat(sp.getPosition().getLng(), sp.getPosition().getLat());

        // 5) Compute travel cost: SP → rec1 → rec2 → ... → recN → SP
        double totalMoves = 0;

        // SP → first delivery
        DeliveryPathDTO.LngLat first = new DeliveryPathDTO.LngLat( recs.get(0).getDelivery().getLng(), recs.get(0).getDelivery().getLat());
        totalMoves += estimateMoves(spLoc, first);

        // Between deliveries
        for (int i = 0; i < recs.size() - 1; i++) {
            DeliveryPathDTO.LngLat a = new DeliveryPathDTO.LngLat( recs.get(i).getDelivery().getLng(), recs.get(i).getDelivery().getLat());
            DeliveryPathDTO.LngLat b = new DeliveryPathDTO.LngLat( recs.get(i+1).getDelivery().getLng(), recs.get(i+1).getDelivery().getLat());
            totalMoves += estimateMoves(a, b);
        }

        // last delivery → back to SP
        DeliveryPathDTO.LngLat last = new DeliveryPathDTO.LngLat( recs.get(recs.size() - 1).getDelivery().getLng(),recs.get(recs.size() - 1).getDelivery().getLat());
        totalMoves += estimateMoves(last, spLoc);

        // 6) Convert moves → cost
        double cost =
                c.getCostInitial() +
                        (totalMoves * c.getCostPerMove()) +
                        c.getCostFinal();

        // 7) Check each dispatch has maxCost ≥ total cost
        for (MedDispatchRec rec : recs) {
            Double maxCost = rec.getRequirements().getMaxCost();
            if (maxCost != null && cost > maxCost) {
                System.out.println(d.getId()+"not passed cost check");

                return false;
            }
        }
        System.out.println(d.getId());
        return true;
    }





    private boolean canHandle(Drone d, MedDispatchRec rec)
    {

        Capability c = d.getCapability();
        MedDispatchRec.Requirements r = rec.getRequirements();

        // 1) Check availability
        if (!isAvailable(d, rec.getDate(), rec.getTime())) {
            return false;
        }

        // 2) Capacity check
        if (r.getCapacity() != null && c.getCapacity() < r.getCapacity()) {
            return false;
        }

        // 3) Cooling/heating check
        if (Boolean.TRUE.equals(r.getCooling()) && !c.isCooling()) {
            return false;
        }

        if (Boolean.TRUE.equals(r.getHeating()) && !c.isHeating()) {
            return false;
        }
        // 4) Estimated cost
        List<ServicePoint> sps = ilp.fetchServicePoints();

        Map<Integer, ServicePoint> spById = sps.stream()
                .collect(Collectors.toMap(ServicePoint::getId, sp -> sp));
        if (r.getMaxCost() != null) {

            ServicePoint sp = spById.get(d.getServicePointId());
            if (sp == null) return false;

            DeliveryPathDTO.LngLat spPos = sp.getPosition();
            DeliveryPathDTO.LngLat dest = new DeliveryPathDTO.LngLat(rec.getDelivery().getLng(), rec.getDelivery().getLat());

            int movesOneWay   = estimateMoves(spPos, dest);

            int movesRoundTrip = movesOneWay * 2;

            double estCost =
                    c.getCostInitial()
                            + movesRoundTrip * c.getCostPerMove()
                            + c.getCostFinal();

            if (estCost > r.getMaxCost()) {
                return false;
            }
        }

        return true;
    }

//also used below
    private int estimateMoves(DeliveryPathDTO.LngLat a,
                              DeliveryPathDTO.LngLat b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        double dx = a.getLng() - b.getLng();
        double dy = a.getLat() - b.getLat();
        double dist = Math.sqrt(dx*dx + dy*dy);
        return (int) Math.ceil(dist / STEP);
    }

    /// section for Delivery Path
    ///
    ///
    private boolean canHandleNextLoad(
            Drone drone,
            DeliveryPathDTO.DronePath dronePath,
            MedDispatchRec nextRec,
            ServicePoint sp,
            Map<Integer, MedDispatchRec> originalDispatches

    ) {
        Capability c = drone.getCapability();

        System.out.println("---- canHandleNextLoad ----");
        System.out.println("Drone " + drone.getId()
                + " (SP=" + sp.getId() + ") checking nextRec=" + nextRec.getId());

        // 1) Availability check
        if (!isAvailable(drone, nextRec.getDate(), nextRec.getTime())) {
            System.out.println("  FAIL: not available at " + nextRec.getDate() + " " + nextRec.getTime());
            return false;
        }
        // 2) Capacity used so far
        double usedCapacity =
                dronePath.getDeliveries()
                        .stream()
                        .mapToDouble(dp -> {
                            MedDispatchRec r = originalDispatches.get(dp.getDeliveryId());
                            if (r == null) {
                                System.out.println("WARN: using fallback capacity lookup for " + dp.getDeliveryId());
                                return 0.0;
                            }
                            return r.getRequirements().getCapacity();
                        })
                        .sum();


        double nextCap = nextRec.getRequirements().getCapacity();
        System.out.println("  usedCapacity so far = " + usedCapacity
                + ", nextCap = " + nextCap
                + ", droneCap = " + c.getCapacity());

        if (usedCapacity + nextCap > c.getCapacity())
        {
            System.out.println("  FAIL: capacity exceeded ("
                    + (usedCapacity + nextCap) + " > " + c.getCapacity() + ")");
            return false;
        }



        // 3) Heating / Cooling
        if (Boolean.TRUE.equals(nextRec.getRequirements().getCooling()) && !c.isCooling())
            return false;

        if (Boolean.TRUE.equals(nextRec.getRequirements().getHeating()) && !c.isHeating())
            return false;

        // 4) Determine current drone position:
        DeliveryPathDTO.LngLat currentPos;

        if (dronePath.getDeliveries().isEmpty()) {
            currentPos = sp.getPosition(); // start at service point
            System.out.println("  currentPos = service point: "
                    + currentPos.getLng() + "," + currentPos.getLat());
        } else {
            DeliveryPathDTO.DeliveryPath lastDel = dronePath.getDeliveries()
                    .get(dronePath.getDeliveries().size() - 1);

            List<DeliveryPathDTO.LngLat> f = lastDel.getFlightPath();
            currentPos = f.get(f.size() - 1); // last coordinate
            System.out.println("  currentPos = last delivery end: "
                    + currentPos.getLng() + "," + currentPos.getLat());
        }

        // 5) Estimate travel moves required for:
        //    current → next → back to SP
        DeliveryPathDTO.LngLat nextPos =
                new DeliveryPathDTO.LngLat(nextRec.getDelivery().getLng(),
                        nextRec.getDelivery().getLat());

        int toNext   = estimateMoves(currentPos, nextPos);
        int hover    = 1;
        int toReturn = estimateMoves(nextPos, sp.getPosition());
        int required = toNext + hover + toReturn;

        // Moves already used so far
        int usedMoves = dronePath.getDeliveries().stream()
                .mapToInt(d -> d.getFlightPath().size())
                .sum();

        int remainingMoves = c.getMaxMoves() - usedMoves;
        System.out.println("  usedMoves = " + usedMoves
                + ", requiredForNext = " + required
                + ", remainingMoves = " + remainingMoves);

        if (required > remainingMoves) {
            System.out.println("  FAIL: not enough moves for next+return");

            return false;
        }

        // 6) Cost check (total multi-load cost)
        double totalMovesForAll =
                usedMoves + required;

        double estCost =
                c.getCostInitial() +
                        totalMovesForAll * c.getCostPerMove() +
                        c.getCostFinal();

        Double maxCost = nextRec.getRequirements().getMaxCost();
        System.out.println("  estCost (with this next load) = " + estCost
                + ", nextRec.maxCost = " + maxCost);

        if (maxCost != null && estCost > maxCost) {
            System.out.println("  FAIL: cost exceeds maxCost");
            return false;
        }
        return true;
    }



    public DeliveryPathDTO.CalcResult calcDeliveryPath2(List<MedDispatchRec> dispatches) {
        // defensive

        if (dispatches == null) dispatches = Collections.emptyList();

        // fetch fresh data
        List<Drone> drones = loadMergedDrones(); // or loadMergedDrones() if you keep it
        List<ServicePoint> sps = ilp.fetchServicePoints();
        List<RestrictedZone> zones = ilp.fetchRestrictedAreas();

        // map service points by id
        Map<Integer, ServicePoint> spById = sps.stream()
                .collect(Collectors.toMap(ServicePoint::getId, sp -> sp));
//        System.out.println("=== SERVICE POINTS MAP ===");
//        spById.forEach((k, v) -> System.out.println("SP key=" + k + " name=" + v.getName()));
//        // dispatch pool (id -> record)

        Map<Integer, MedDispatchRec> unassigned = dispatches.stream()
                .collect(Collectors.toMap(MedDispatchRec::getId, d -> d));

        DeliveryPathDTO.CalcResult result = new DeliveryPathDTO.CalcResult();
        int totalMoves = 0;
        double totalCost = 0.0;

//        System.out.println("=== CAN HANDLE CHECK ===");
//        for (Drone d : drones) {
//            for (MedDispatchRec rec : dispatches) {
//                System.out.println(
//                        "Drone " + d.getId() +
//                                " canHandle(" + rec.getId() + ") = " + canHandle(d, rec)
//                );
//            }
//        }

        // Group drones by service point (only drones that have a servicePointId)
        Map<Integer, List<Drone>> dronesBySp = drones.stream()
                .collect(Collectors.groupingBy(Drone::getServicePointId));

        // Iterate over service points (greedy: do each SP separately)
        for (Map.Entry<Integer, List<Drone>> entry : dronesBySp.entrySet()) {

            Integer spId = entry.getKey();
            //System.out.println("Processing SP " + spId + " drones=" + entry.getValue().size());

            ServicePoint sp = spById.get(spId);
            if (sp == null) continue; // safety

            List<Drone> dronesAtSp = entry.getValue();

            // For each drone at this service point, try to assign as many deliveries as possible
            for (Drone drone : dronesAtSp) {
                // Only consider drones that are capable for at least one remaining dispatch by requirements/availability
                List<MedDispatchRec> candidates = unassigned.values().stream()
                        .filter(rec -> canHandle(drone, rec)) // reuse your canHandle() availability/capacity checks
                        .collect(Collectors.toList());


                if (candidates.isEmpty()) continue;

                int remainingMoves = drone.getCapability().getMaxMoves();
                DeliveryPathDTO.LngLat currentPos = sp.getPosition(); // start location
                DeliveryPathDTO.DronePath dronePath = new DeliveryPathDTO.DronePath();
                dronePath.setDroneId(drone.getId());

                double usedCapacity = dronePath.getDeliveries()
                        .stream()
                        .mapToDouble(id ->
                                unassigned.get(id).getRequirements().getCapacity()
                        ).sum();



                // keep going while there are feasible deliveries
                while (true) {
                    // build list of feasible deliveries: must be still unassigned and satisfy requirements,
                    // and enough moves to go there + hover + return to start
                    List<MedDispatchRec> feasible = new ArrayList<>();
                    for (MedDispatchRec rec : unassigned.values()) {
                        if (!canHandle(drone, rec)) continue;

                        DeliveryPathDTO.LngLat dest = toLngLat(rec);
                        // estimate moves: ceil(euclidean / STEP)
                        int toDest = estimateMoves(currentPos, dest);
                        int toReturn = estimateMoves(dest, sp.getPosition());
                        int required = toDest + 1 + toReturn; // +1 for hover

                        if (required <= remainingMoves) feasible.add(rec);
                    }

                    if (feasible.isEmpty()) break;

                    // choose nearest feasible (fewest moves to destination)
                    DeliveryPathDTO.LngLat finalCurrentPos = currentPos;
                    feasible.sort(Comparator.comparingInt(rec -> estimateMoves(finalCurrentPos, toLngLat(rec))));
                    MedDispatchRec chosen = feasible.get(0);

                    // Now get the actual flight path for currentPos -> chosen.location via ILP
                    List<DeliveryPathDTO.LngLat> legPath =
                            calculateFlightPath(currentPos, toLngLat(chosen), zones);

                    if (legPath == null || legPath.isEmpty()) {
                        // ILP couldn't produce a path -> mark as infeasible for this drone
                        // remove from feasible for this drone and continue (simple approach)
                        unassigned.remove(chosen.getId()); // optional: or skip only for this drone
                        continue;
                    }

                    // ILP should return steps from currentPos to dest; we append a hover duplicate
                    List<DeliveryPathDTO.LngLat> flightPath = new ArrayList<>(legPath);
                    // Ensure final equals dest (tolerance)
                    DeliveryPathDTO.LngLat last = flightPath.get(flightPath.size() - 1);
                    if (Math.abs(last.getLat() - toLngLat(chosen).getLat()) > 1e-8 ||
                            Math.abs(last.getLng() - toLngLat(chosen).getLng()) > 1e-8) {
                        // append exact dest if ILP didn't land exactly
                        flightPath.add(toLngLat(chosen));
                    }
                    // add hover (duplicate)
                    flightPath.add(flightPath.get(flightPath.size() - 1));

                    int used = flightPath.size(); // includes hover
                    // Safety: also account for return-to-base moves now (estimate)
                    int returnEstimate = estimateMoves(toLngLat(chosen), sp.getPosition());
                    if (used + returnEstimate > remainingMoves) {
                        // cannot accept this delivery (unexpected) — remove from feasible and continue
                        // to avoid infinite loop, remove it from UNASSIGNED entirely or mark skipped for this drone.
                        // Here we skip it for this drone only:
                        // break to finish drone's route.
                        break;
                    }

                    // Accept this delivery
                    DeliveryPathDTO.DeliveryPath entryDto = new DeliveryPathDTO.DeliveryPath();
                    entryDto.setDeliveryId(chosen.getId());
                    entryDto.setFlightPath(flightPath);
                    dronePath.getDeliveries().add(entryDto);

                    // update counters
                    remainingMoves -= used;
                    totalMoves += used;

                    // mark as assigned (remove from unassigned)
                    unassigned.remove(chosen.getId());

                    // update current position to delivery position (hover end)
                    currentPos = toLngLat(chosen);

                    // continue to try next delivery from currentPos
                } // end while for this drone

                if (!dronePath.getDeliveries().isEmpty()) {
                    // === NEW: add return path from last delivery back to service point ===
                    DeliveryPathDTO.DeliveryPath lastDelivery =
                            dronePath.getDeliveries().get(dronePath.getDeliveries().size() - 1);

                    List<DeliveryPathDTO.LngLat> lastFlight = lastDelivery.getFlightPath();
                    if (lastFlight != null && !lastFlight.isEmpty()) {
                        DeliveryPathDTO.LngLat lastPos = lastFlight.get(lastFlight.size() - 1);

                        // Build SP position as LngLat
                        DeliveryPathDTO.LngLat spPos =
                                new DeliveryPathDTO.LngLat(sp.getPosition().getLng(), sp.getPosition().getLat());

                        // Compute actual path from lastPos back to SP
                        List<DeliveryPathDTO.LngLat> returnPath =
                                calculateFlightPath(lastPos, spPos, zones);

                        if (returnPath != null && !returnPath.isEmpty()) {
                            // Avoid duplicating the starting point of the return leg
                            // (it is already the last point of lastFlight)
                            for (int i = 1; i < returnPath.size(); i++) {
                                lastFlight.add(returnPath.get(i));
                            }

                            // Update remainingMoves and totalMoves with actual return steps
                            remainingMoves -= (returnPath.size() - 1);
                            totalMoves += (returnPath.size() - 1);
                        }
                    }

                    // Now compute cost including this return leg
                    int movesUsed = drone.getCapability().getMaxMoves() - remainingMoves;
                    double droneCost = drone.getCapability().getCostInitial()
                            + movesUsed * drone.getCapability().getCostPerMove()
                            + drone.getCapability().getCostFinal();
                    totalCost += droneCost;

                    result.getDronePaths().add(dronePath);
                }

                // early exit if all deliveries assigned
                if (unassigned.isEmpty()) break;
            } // end drones at SP

            if (unassigned.isEmpty()) break;
        } // end per service point

        result.setTotalMoves(totalMoves);
        result.setTotalCost(totalCost);
        return result;
    }


    public DeliveryPathDTO.CalcResult calcDeliveryPath3(List<MedDispatchRec> dispatches) {
        Map<Integer, MedDispatchRec> originalDispatchMap =
                dispatches.stream().collect(Collectors.toMap(MedDispatchRec::getId, d -> d));


        if (dispatches == null) dispatches = Collections.emptyList();

        // Fresh data
        List<Drone> drones = loadMergedDrones();
        List<ServicePoint> sps = ilp.fetchServicePoints();
        List<RestrictedZone> zones = ilp.fetchRestrictedAreas();

        Map<Integer, ServicePoint> spById = sps.stream()
                .collect(Collectors.toMap(ServicePoint::getId, sp -> sp));

        Map<Integer, MedDispatchRec> allDispatchesById = dispatches.stream()
                .collect(Collectors.toMap(MedDispatchRec::getId, d -> d));

        Map<Integer, MedDispatchRec> unassigned = new HashMap<>(allDispatchesById);


        DeliveryPathDTO.CalcResult result = new DeliveryPathDTO.CalcResult();
        int totalMoves = 0;
        double totalCost = 0.0;

        // Drones per service point
        Map<Integer, List<Drone>> dronesBySp =
                drones.stream().collect(Collectors.groupingBy(Drone::getServicePointId));

        // PROCESS SERVICE POINTS
        for (Map.Entry<Integer, List<Drone>> entry : dronesBySp.entrySet()) {

            int spId = entry.getKey();
            ServicePoint sp = spById.get(spId);
            if (sp == null) continue;

            List<Drone> dronesAtSp = entry.getValue();

            for (Drone drone : dronesAtSp) {

                int remainingMoves = drone.getCapability().getMaxMoves();
                double usedCapacity = 0;

                DeliveryPathDTO.LngLat currentPos = sp.getPosition();
                DeliveryPathDTO.DronePath dronePath = new DeliveryPathDTO.DronePath();
                dronePath.setDroneId(drone.getId());

                // MAIN LOOP FOR THIS DRONE
                while (true) {

                    List<MedDispatchRec> feasible = unassigned.values().stream()
                            .filter(rec -> canHandleNextLoad(
                                    drone,
                                    dronePath,
                                    rec,
                                    sp,
                                    originalDispatchMap
                            ))
                            .collect(Collectors.toList());

                    if (feasible.isEmpty()) break;


                    // pick nearest
                    DeliveryPathDTO.LngLat finalCurrentPos = currentPos;
                    feasible.sort(Comparator.comparingInt(
                            rec -> estimateMoves(finalCurrentPos, toLngLat(rec))
                    ));

                    MedDispatchRec chosen = feasible.get(0);

                    // FLY TO DEST
                    DeliveryPathDTO.LngLat dest = toLngLat(chosen);

                    List<DeliveryPathDTO.LngLat> legPath =
                            calculateFlightPath(currentPos, dest, zones);

                    if (legPath == null || legPath.isEmpty()) {
                        // skip this delivery forever
                        unassigned.remove(chosen.getId());
                        continue;
                    }

//                    // ensure exact target + hover
//                    List<DeliveryPathDTO.LngLat> flightPath = new ArrayList<>(legPath);
//                    DeliveryPathDTO.LngLat last = flightPath.get(flightPath.size() - 1);
//
//                    if (Math.abs(last.getLat() - dest.getLat()) > 1e-8 ||
//                            Math.abs(last.getLng() - dest.getLng()) > 1e-8) {
//                        flightPath.add(dest);
//                    }
//                    flightPath.add(dest); // hover
//
//                    int used = flightPath.size();
//
//                    // commit
//                    DeliveryPathDTO.DeliveryPath dp = new DeliveryPathDTO.DeliveryPath();
//                    dp.setDeliveryId(chosen.getId());
//                    dp.setFlightPath(flightPath);
//                    dronePath.getDeliveries().add(dp);
//
//                    remainingMoves -= used;
//                    totalMoves += used;
//                    usedCapacity += chosen.getRequirements().getCapacity();
//
//                    unassigned.remove(chosen.getId());
//                    currentPos = dest;
                    List<DeliveryPathDTO.LngLat> flightPath = new ArrayList<>(legPath);
                    DeliveryPathDTO.LngLat last = flightPath.get(flightPath.size() - 1);

                    // Ensure exact target
                    if (Math.abs(last.getLat() - chosen.getDelivery().getLat()) > 1e-8 ||
                            Math.abs(last.getLng() - chosen.getDelivery().getLng()) > 1e-8) {
                        flightPath.add(new DeliveryPathDTO.LngLat(
                                chosen.getDelivery().getLng(),
                                chosen.getDelivery().getLat()));
                    }

                    // Add hover
                    flightPath.add(flightPath.get(flightPath.size() - 1));

                    DeliveryPathDTO.DeliveryPath dp = new DeliveryPathDTO.DeliveryPath();
                    dp.setDeliveryId(chosen.getId());
                    dp.setFlightPath(flightPath);

                    dronePath.getDeliveries().add(dp);

                    // Update current position
                    currentPos = new DeliveryPathDTO.LngLat(
                            chosen.getDelivery().getLng(),
                            chosen.getDelivery().getLat());

                    // Remove assignment
                    unassigned.remove(chosen.getId());
                }

                // RETURN TO SP
                if (!dronePath.getDeliveries().isEmpty()) {

                    DeliveryPathDTO.DeliveryPath lastDelivery =
                            dronePath.getDeliveries().get(dronePath.getDeliveries().size() - 1);

                    List<DeliveryPathDTO.LngLat> lastFlight = lastDelivery.getFlightPath();
                    DeliveryPathDTO.LngLat lastPos = lastFlight.get(lastFlight.size() - 1);

                    DeliveryPathDTO.LngLat spLoc =
                            new DeliveryPathDTO.LngLat(sp.getPosition().getLng(), sp.getPosition().getLat());

                    List<DeliveryPathDTO.LngLat> returnPath =
                            calculateFlightPath(lastPos, spLoc, zones);

                    if (returnPath != null && !returnPath.isEmpty()) {
                        for (int i = 1; i < returnPath.size(); i++)
                            lastFlight.add(returnPath.get(i));
//                        DeliveryPathDTO.DeliveryPath returnDp = new DeliveryPathDTO.DeliveryPath();
//                        returnDp.setDeliveryId(-1); // special ID for "return"
//                        returnDp.setFlightPath(returnPath);
//
//                        dronePath.getDeliveries().add(returnDp);

                        remainingMoves -= (returnPath.size() - 1);
                        totalMoves += (returnPath.size() - 1);
                    }

                    // COST
                    int movesUsed = drone.getCapability().getMaxMoves() - remainingMoves;
                    double droneCost =
                            drone.getCapability().getCostInitial() +
                                    movesUsed * drone.getCapability().getCostPerMove() +
                                    drone.getCapability().getCostFinal();

                    totalCost += droneCost;

                    result.getDronePaths().add(dronePath);
                }

                if (unassigned.isEmpty()) break;
            }

            if (unassigned.isEmpty()) break;
        }

        result.setTotalMoves(totalMoves);
        result.setTotalCost(totalCost);
        return result;
    }
    public DeliveryPathDTO.CalcResult calcDeliveryPathCW2(List<MedDispatchRec> dispatches) {

        if (dispatches == null) {
            dispatches = Collections.emptyList();
        }

        // Fresh data
        List<Drone> drones = loadMergedDrones();
        List<ServicePoint> sps = ilp.fetchServicePoints();
        List<RestrictedZone> zones = ilp.fetchRestrictedAreas();

        Map<Integer, ServicePoint> spById = sps.stream()
                .collect(Collectors.toMap(ServicePoint::getId, sp -> sp));

        // unassigned pool
        Map<Integer, MedDispatchRec> unassigned = dispatches.stream()
                .collect(Collectors.toMap(MedDispatchRec::getId, d -> d));

        DeliveryPathDTO.CalcResult result = new DeliveryPathDTO.CalcResult();
        int totalMoves = 0;
        double totalCost = 0.0;

        // Group drones by service point
        Map<Integer, List<Drone>> dronesBySp =
                drones.stream().collect(Collectors.groupingBy(Drone::getServicePointId));

        // Iterate over SPs
        for (Map.Entry<Integer, List<Drone>> spEntry : dronesBySp.entrySet()) {

            int spId = spEntry.getKey();
            ServicePoint sp = spById.get(spId);
            if (sp == null) continue;

            List<Drone> dronesAtSp = spEntry.getValue();

            // For each drone at this SP, allow multiple independent trips
            for (Drone drone : dronesAtSp) {

                Capability cap = drone.getCapability();
                int remainingMoves = cap.getMaxMoves();

                DeliveryPathDTO.DronePath dronePath = new DeliveryPathDTO.DronePath();
                dronePath.setDroneId(drone.getId());

                // While we can still do trips with this drone
                while (true) {
                    if (unassigned.isEmpty()) break;

                    // Find feasible deliveries: drone can handle it AND enough moves SP→d→SP
                    DeliveryPathDTO.LngLat spPos =
                            new DeliveryPathDTO.LngLat(sp.getPosition().getLng(), sp.getPosition().getLat());

                    int finalRemainingMoves = remainingMoves;
                    List<MedDispatchRec> feasible = unassigned.values().stream()
                            .filter(rec -> canHandle(drone, rec))  // capacity, cooling/heating, cost per trip
                            .filter(rec -> {
                                DeliveryPathDTO.LngLat dest = toLngLat(rec);
                                int toDest = estimateMoves(spPos, dest);
                                int toReturn = estimateMoves(dest, spPos);
                                int required = toDest + 1 + toReturn; // +1 hover
                                return required <= finalRemainingMoves;
                            })
                            .collect(Collectors.toList());

                    if (feasible.isEmpty()) {
                        break; // no more trips for this drone
                    }

                    // pick nearest by moves from SP
                    MedDispatchRec chosen = feasible.stream()
                            .min(Comparator.comparingInt(rec -> estimateMoves(spPos, toLngLat(rec))))
                            .orElseThrow();

                    DeliveryPathDTO.LngLat dest = toLngLat(chosen);

                    // Outbound SP -> dest
                    List<DeliveryPathDTO.LngLat> outPath =
                            aStarPath(spPos, dest, zones);

                    if (outPath == null || outPath.isEmpty()) {
                        // skip this delivery permanently to avoid infinite loops
                        unassigned.remove(chosen.getId());
                        continue;
                    }



                    // Build full path for this delivery: out + hover + back
                    List<DeliveryPathDTO.LngLat> fullPath = new ArrayList<>(outPath);
                    System.out.println("outpath="+fullPath);
                    DeliveryPathDTO.LngLat last = fullPath.get(fullPath.size() - 1);
                    if (Math.abs(last.getLat() - dest.getLat()) > 1e-8 ||
                            Math.abs(last.getLng() - dest.getLng()) > 1e-8) {
                        fullPath.add(last);
                    }

                    //fullPath.add(fullPath.get(fullPath.size() - 1));
                    System.out.println("outpath + hover="+fullPath);

                    // Return dest -> SP
                    List<DeliveryPathDTO.LngLat> backPath =
                            aStarPath(last, spPos, zones);

                    if (backPath != null && !backPath.isEmpty()) {
                        for (int i = 1; i < backPath.size(); i++) {
                            fullPath.add(backPath.get(i));
                        }
                    }
                    ///fullPath.add(spPos); if not n steps

                    int used = fullPath.size();
                    if (used > remainingMoves) {
                        // safety; should not happen because of earlier check
                        break;
                    }

                    // Commit this delivery trip
                    DeliveryPathDTO.DeliveryPath dp = new DeliveryPathDTO.DeliveryPath();
                    dp.setDeliveryId(chosen.getId());
                    dp.setFlightPath(fullPath);
                    dronePath.getDeliveries().add(dp);

                    remainingMoves -= used;
                    totalMoves += used;
                    unassigned.remove(chosen.getId());
                }

                if (!dronePath.getDeliveries().isEmpty()) {
                    int movesUsed = cap.getMaxMoves() - remainingMoves;
                    double droneCost =
                            cap.getCostInitial() +
                                    movesUsed * cap.getCostPerMove() +
                                    cap.getCostFinal();
                    totalCost += droneCost;

                    result.getDronePaths().add(dronePath);
                }

                if (unassigned.isEmpty()) break;
            }

            if (unassigned.isEmpty()) break;
        }

        result.setTotalMoves(totalMoves);
        result.setTotalCost(totalCost);
        return result;
    }



    private DeliveryPathDTO.LngLat toLngLat(MedDispatchRec rec) {
        if (rec.getDelivery() == null)
            throw new IllegalArgumentException("Delivery must contain location.");

        return new DeliveryPathDTO.LngLat(
                rec.getDelivery().getLng(),
                rec.getDelivery().getLat()
        );
    }

    /// path finding algo
    ///
    ///
    ///




    public List<DeliveryPathDTO.LngLat> calculateFlightPath2(DeliveryPathDTO.LngLat from,
                                                            DeliveryPathDTO.LngLat to,
                                                            List<RestrictedZone> restrictedZones) {

        if (from == null || to == null) return null;
        // trivial case
        if (Math.abs(from.getLat() - to.getLat()) < EPS && Math.abs(from.getLng() - to.getLng()) < EPS) {
            // Already at the target: return one-point path (the caller will duplicate for hover)
            return List.of(new DeliveryPathDTO.LngLat(from.getLng(), from.getLat()));
        }


        double curLng = from.getLng();
        double curLat = from.getLat();
        double targetLng = to.getLng();
        double targetLat = to.getLat();

        List<DeliveryPathDTO.LngLat> path = new ArrayList<>();
        path.add(new DeliveryPathDTO.LngLat(curLng, curLat));

        double dist = euclid(curLng, curLat, targetLng, targetLat);

        // safe caps
        int maxSteps = Math.max(20000, (int)Math.ceil(dist / STEP) * 50); // generous cap
        Instant start = Instant.now();
        int steps = 0;

        while (dist > (STEP + 1e-12) && steps < maxSteps) {
            steps++;

            double bestDist = Double.POSITIVE_INFINITY;
            double bestLng = Double.NaN, bestLat = Double.NaN;
            boolean found = false;

            // examine the 16 candidate directions
            for (double angleDeg : ALLOWED_ANGLES) {
                double rad = Math.toRadians(angleDeg);
                double candLng = curLng + Math.cos(rad) * STEP;
                double candLat = curLat + Math.sin(rad) * STEP;

                // if candidate is inside any restricted zone, skip
                if (isPointInAnyRestricted(candLng, candLat, restrictedZones)) continue;

                // prefer candidate that strictly reduces distance to target
                double candDist = euclid(candLng, candLat, targetLng, targetLat);
                if (candDist + 1e-12 < dist) { // strictly closer
                    if (candDist < bestDist) {
                        bestDist = candDist;
                        bestLng = candLng;
                        bestLat = candLat;
                        found = true;
                    }
                }
            }

            if (!found) {
                // if none of the 16 directions reduce the distance (all blocked or not moving closer),
                // try a relaxed mode: allow equal-distance moves to escape a local plateau (rare).
                for (double angleDeg : ALLOWED_ANGLES) {
                    double rad = Math.toRadians(angleDeg);
                    double candLng = curLng + Math.cos(rad) * STEP;
                    double candLat = curLat + Math.sin(rad) * STEP;
                    if (isPointInAnyRestricted(candLng, candLat, restrictedZones)) continue;
                    double candDist = euclid(candLng, candLat, targetLng, targetLat);
                    if (candDist < bestDist) {
                        bestDist = candDist;
                        bestLng = candLng;
                        bestLat = candLat;
                        found = true;
                    }
                }
            }

            if (!found) {
                // give up — no valid candidate
                System.out.println("NO VALID STEP FOUND — path blocked or restricted.");

                return null;
            }

            // commit the best step
            curLng = bestLng;
            curLat = bestLat;
            path.add(new DeliveryPathDTO.LngLat(curLng, curLat));
            dist = bestDist;

            // time safety: stop if pathfinding takes too long per leg
            if (Duration.between(start, Instant.now()).toMillis() > 5000) {
                return null;
            }
        }

//        // append exact target if not already there
//        DeliveryPathDTO.LngLat last = path.get(path.size()-1);
//        if (Math.abs(last.getLat() - targetLat) > 1e-9 || Math.abs(last.getLng() - targetLng) > 1e-9) {
//            // ensure final target is not inside restricted zone
//            if (isPointInAnyRestricted(targetLng, targetLat, restrictedZones)) return null;
//            path.add(new DeliveryPathDTO.LngLat(targetLng, targetLat));
//        }
//
//
//        path = removeConsecutiveDuplicates(path);
        //path = tidyEndpointsOnly(path);

        return path;
    }
    public List<DeliveryPathDTO.LngLat> calculateFlightPath3(DeliveryPathDTO.LngLat from, //this cross the resreictedpoint (edge crosses not vertices)
                                                            DeliveryPathDTO.LngLat to,
                                                            List<RestrictedZone> restrictedZones) {

        if (from == null || to == null) return null;
        // trivial case
        if (Math.abs(from.getLat() - to.getLat()) < EPS && Math.abs(from.getLng() - to.getLng()) < EPS) {
            // Already at the target: return one-point path (the caller will duplicate for hover)
            return List.of(new DeliveryPathDTO.LngLat(from.getLng(), from.getLat()));
        }

        double curLng = from.getLng();
        double curLat = from.getLat();
        double targetLng = to.getLng();
        double targetLat = to.getLat();

        List<DeliveryPathDTO.LngLat> path = new ArrayList<>();
        path.add(new DeliveryPathDTO.LngLat(curLng, curLat));

        double dist = euclid(curLng, curLat, targetLng, targetLat);

        // safe caps
        int maxSteps = Math.max(20000, (int)Math.ceil(dist / STEP) * 50); // generous cap
        Instant start = Instant.now();
        int steps = 0;

        while (dist > (STEP + 1e-12) && steps < maxSteps) {
            steps++;

            double bestDist = Double.POSITIVE_INFINITY;
            double bestLng = Double.NaN, bestLat = Double.NaN;
            boolean found = false;

            // examine the 16 candidate directions
            for (double angleDeg : ALLOWED_ANGLES) {
                double rad = Math.toRadians(angleDeg);
                double candLng = curLng + Math.cos(rad) * STEP;
                double candLat = curLat + Math.sin(rad) * STEP;

                // if candidate is inside any restricted zone, skip
                if (isPointInAnyRestricted(candLng, candLat, restrictedZones)) continue;

                // prefer candidate that strictly reduces distance to target
                double candDist = euclid(candLng, candLat, targetLng, targetLat);
                if (candDist + 1e-12 < dist) { // strictly closer
                    if (candDist < bestDist) {
                        bestDist = candDist;
                        bestLng = candLng;
                        bestLat = candLat;
                        found = true;
                    }
                }
            }

            if (!found) {

                for (double angleDeg : ALLOWED_ANGLES) {
                    double rad = Math.toRadians(angleDeg);
                    double candLng = curLng + Math.cos(rad) * STEP;
                    double candLat = curLat + Math.sin(rad) * STEP;
                    if (isPointInAnyRestricted(candLng, candLat, restrictedZones)) continue;
                    if (segmentIntersectsAnyRestricted(
                            curLng, curLat, candLng, candLat, restrictedZones)) continue;
                    double candDist = euclid(candLng, candLat, targetLng, targetLat);
                    if (candDist < bestDist) {
                        bestDist = candDist;
                        bestLng = candLng;
                        bestLat = candLat;
                        found = true;
                    }
                }
            }

            if (!found) {
                // give up — no valid candidate
                System.out.println("NO VALID STEP FOUND — path blocked or restricted.");

                return null;
            }

            // commit the best step
            curLng = bestLng;
            curLat = bestLat;
            path.add(new DeliveryPathDTO.LngLat(curLng, curLat));
            dist = bestDist;

            // time safety: stop if pathfinding takes too long per leg
            if (Duration.between(start, Instant.now()).toMillis() > 4000) {
                // 4 seconds per leg is enough for coursework; bail out
                return null;
            }
        }

        // append exact target if not already there
        DeliveryPathDTO.LngLat last = path.get(path.size()-1);
        if (Math.abs(last.getLat() - targetLat) > 1e-9 || Math.abs(last.getLng() - targetLng) > 1e-9) {
            // ensure final target is not inside restricted zone
            if (isPointInAnyRestricted(targetLng, targetLat, restrictedZones)) return null;
            path.add(new DeliveryPathDTO.LngLat(targetLng, targetLat));
        }

        return path;
    }
    public List<DeliveryPathDTO.LngLat> calculateFlightPath(
            DeliveryPathDTO.LngLat from,
            DeliveryPathDTO.LngLat to,
            List<RestrictedZone> restrictedZones) {

        return aStarPath(from, to, restrictedZones);
    }

    public List<DeliveryPathDTO.LngLat> calculateFlightPath4(//debugging
            DeliveryPathDTO.LngLat from,
            DeliveryPathDTO.LngLat to,
            List<RestrictedZone> restrictedZones) {

        System.out.println("\n=== calculateFlightPath ===");
        System.out.println("FROM: " + from.getLng() + ", " + from.getLat());
        System.out.println("TO:   " + to.getLng() + ", " + to.getLat());

        if (from == null || to == null) {
            System.out.println("FAIL: from or to is null");
            return null;
        }

        if (Math.abs(from.getLat() - to.getLat()) < EPS &&
                Math.abs(from.getLng() - to.getLng()) < EPS) {

            System.out.println("Trivial: start == end");
            return List.of(new DeliveryPathDTO.LngLat(from.getLng(), from.getLat()));
        }

        double curLng = from.getLng();
        double curLat = from.getLat();
        double targetLng = to.getLng();
        double targetLat = to.getLat();

        List<DeliveryPathDTO.LngLat> path = new ArrayList<>();
        path.add(new DeliveryPathDTO.LngLat(curLng, curLat));

        double dist = euclid(curLng, curLat, targetLng, targetLat);
        System.out.println("Initial straight-line distance = " + dist);

        int maxSteps = Math.max(20000, (int)Math.ceil(dist / STEP) * 50);
        Instant start = Instant.now();
        int steps = 0;

        while (dist > (STEP + 1e-12) && steps < maxSteps) {
            steps++;

            System.out.println("\n--- Step " + steps + " ---");
            System.out.println("Current: " + curLng + ", " + curLat);

            double bestDist = Double.POSITIVE_INFINITY;
            double bestLng = Double.NaN, bestLat = Double.NaN;
            boolean found = false;

            // ---------- MAIN LOOP ----------
            System.out.println("Trying STRICT-CLOSER candidates:");
            for (double angleDeg : ALLOWED_ANGLES) {
                double rad = Math.toRadians(angleDeg);
                double candLng = curLng + Math.cos(rad) * STEP;
                double candLat = curLat + Math.sin(rad) * STEP;

                System.out.println(" angle " + angleDeg + "° → (" + candLng + ", " + candLat + ")");

                // point check
                if (isPointInAnyRestricted(candLng, candLat, restrictedZones)) {
                    System.out.println("   REJECT: candidate INSIDE restricted zone");
                    continue;
                }

                // line check
                if (segmentIntersectsAnyRestricted(curLng, curLat, candLng, candLat, restrictedZones)) {
                    System.out.println("   REJECT: segment INTERSECTS restricted zone edges");
                    continue;
                }

                double candDist = euclid(candLng, candLat, targetLng, targetLat);
                System.out.println("   candDist = " + candDist + " (current best = " + bestDist + ")");

                if (candDist + 1e-12 < dist && candDist < bestDist) {
                    found = true;
                    bestDist = candDist;
                    bestLng = candLng;
                    bestLat = candLat;
                    System.out.println("   ACCEPT as new best step");
                }
            }

            // ---------- FALLBACK LOOP ----------
            if (!found) {
                // FALLBACK #1: equaldistance moves
                for (double angleDeg : ALLOWED_ANGLES) {
                    double rad = Math.toRadians(angleDeg);
                    double candLng = curLng + Math.cos(rad) * STEP;
                    double candLat = curLat + Math.sin(rad) * STEP;

                    if (isPointInAnyRestricted(candLng, candLat, restrictedZones)) continue;
                    if (segmentIntersectsAnyRestricted(curLng, curLat, candLng, candLat, restrictedZones)) continue;

                    double candDist = euclid(candLng, candLat, targetLng, targetLat);
                    if (Math.abs(candDist - dist) < 1e-12) { // equal-distance move
                        bestLng = candLng;
                        bestLat = candLat;
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                // FALLBACK #2: allow a  distance increase
                // required to go around polygon corners
                double bestIncrease = Double.POSITIVE_INFINITY;

                for (double angleDeg : ALLOWED_ANGLES) {
                    double rad = Math.toRadians(angleDeg);
                    double candLng = curLng + Math.cos(rad) * STEP;
                    double candLat = curLat + Math.sin(rad) * STEP;

                    if (isPointInAnyRestricted(candLng, candLat, restrictedZones)) continue;
                    if (segmentIntersectsAnyRestricted(curLng, curLat, candLng, candLat, restrictedZones)) continue;

                    double candDist = euclid(candLng, candLat, targetLng, targetLat);

                    // allow ≤ 5% increase in distance
                    if (candDist <= dist * 3 && candDist < bestIncrease) {
                        bestIncrease = candDist;
                        bestLng = candLng;
                        bestLat = candLat;
                        found = true;
                    }
                }
            }

            // ---------- NO CANDIDATES ----------
            if (!found) {
                System.out.println("NO VALID STEP FOUND — completely blocked.");
                return null;
            }

            // commit move
            curLng = bestLng;
            curLat = bestLat;
            path.add(new DeliveryPathDTO.LngLat(curLng, curLat));
            dist = bestDist;

            System.out.println("Moved to " + curLng + ", " + curLat);
            System.out.println("Remaining distance = " + dist);

            if (Duration.between(start, Instant.now()).toMillis() > 40000) {
                System.out.println("FAIL: TIMEOUT (4 seconds) during path computation");
                return null;
            }
        }

        // ---------- FINISH ----------
        DeliveryPathDTO.LngLat last = path.get(path.size() - 1);

        if (Math.abs(last.getLat() - targetLat) > 1e-9 ||
                Math.abs(last.getLng() - targetLng) > 1e-9) {

            System.out.println("Adding final target check for intersection...");

            if (isPointInAnyRestricted(targetLng, targetLat, restrictedZones)) {
                System.out.println("FAIL: final target lies inside restricted zone");
                return null;
            }
            if (segmentIntersectsAnyRestricted(last.getLng(), last.getLat(), targetLng, targetLat, restrictedZones)) {
                System.out.println("FAIL: last segment to target INTERSECTS restricted zone");
                return null;
            }

            path.add(new DeliveryPathDTO.LngLat(targetLng, targetLat));
            System.out.println("Final target appended.");
        }

        System.out.println("=== PATH COMPLETE: " + path.size() + " steps ===");
        return path;
    }





    /** Euclidean distance on plain lat/lng coords (sufficient for small local distances in coursework) */
    private double euclid(double lng1, double lat1, double lng2, double lat2) {
        double dx = lng1 - lng2;
        double dy = lat1 - lat2;
        return Math.sqrt(dx*dx + dy*dy);
    }

    /** Check whether (lng,lat) is inside any restricted polygon. */
    private boolean isPointInAnyRestricted(double lng, double lat, List<RestrictedZone> zones) {
        if (zones == null || zones.isEmpty()) return false;
        Position p = new Position(lng, lat);

        for (RestrictedZone z : zones) {
            if (z.getVertices() == null || z.getVertices().isEmpty()) continue;
            try {
                if (s.isInRegion(p, z.getVertices())) {
                    return true;
                }
            } catch (Exception ex) {
                // ignore malformed regions
            }
        }
        return false;
    }
    private boolean segmentIntersectsAnyRestricted(
            double x1, double y1, double x2, double y2,
            List<RestrictedZone> zones
    ) {
        for (RestrictedZone z : zones) {
            List<Position> verts = z.getVertices();
            for (int i = 0; i < verts.size() - 1; i++) {
                Position a = verts.get(i);
                Position b = verts.get(i+1);

                if (segmentsIntersect(
                        x1, y1, x2, y2,
                        a.getLng(), a.getLat(),
                        b.getLng(), b.getLat()
                )) return true;
            }
        }
        return false;
    }
    private boolean segmentsIntersect(
            double x1, double y1, double x2, double y2,
            double x3, double y3, double x4, double y4
    ) {
        return ccw(x1,y1, x3,y3, x4,y4) != ccw(x2,y2, x3,y3, x4,y4) &&
                ccw(x1,y1, x2,y2, x3,y3) != ccw(x1,y1, x2,y2, x4,y4);
    }

    private boolean ccw(double ax, double ay, double bx, double by, double cx, double cy) {
        return (cy - ay) * (bx - ax) > (by - ay) * (cx - ax);
    }
    private static class Node {
        final double lng;
        final double lat;

        Node(double lng, double lat) {
            this.lng = lng;
            this.lat = lat;
        }

        public double getLng() {
            return lng;
        }

        public double getLat() {
            return lat;
        }
    }

    public List<DeliveryPathDTO.LngLat> aStarPath(
            DeliveryPathDTO.LngLat start,
            DeliveryPathDTO.LngLat goal,
            List<RestrictedZone> restrictedZones) {

        record State(Node n, double f, double g) {}


        Function<Node, String> key = n -> {
            long x = Math.round(n.getLng() * 1_000_000);
            long y = Math.round(n.getLat() * 1_000_000);
            return x + ":" + y;
        };

        Node startN = new Node(start.getLng(), start.getLat());
        Node goalN  = new Node(goal.getLng(),  goal.getLat());

        PriorityQueue<State> open = new PriorityQueue<>(Comparator.comparingDouble(s -> s.f()));

        Map<String, Double> gScore = new HashMap<>();
        gScore.put(key.apply(startN), 0.0);

        Map<String, Node> cameFrom = new HashMap<>();

        open.add(new State(startN, heuristic(startN, goalN), 0.0));

        while (!open.isEmpty()) {

            State current = open.poll();
            Node cur = current.n();
            String curKey = key.apply(cur);

            // Reached goal (within 1 step)
            if (euclid(cur.getLng(), cur.getLat(), goalN.getLng(), goalN.getLat()) <= STEP) {
                return reconstruct(cameFrom, cur, key);
            }

            for (double angleDeg : ALLOWED_ANGLES) {
                double rad = Math.toRadians(angleDeg);

                double nx = cur.getLng() + Math.cos(rad) * STEP;
                double ny = cur.getLat() + Math.sin(rad) * STEP;

                Node next = new Node(nx, ny);
                String nextKey = key.apply(next);

                // Restricted zone checks
                if (isPointInAnyRestricted(nx, ny, restrictedZones)) continue;
                if (segmentIntersectsAnyRestricted(
                        cur.getLng(), cur.getLat(), nx, ny, restrictedZones))
                    continue;

                double tentative = gScore.get(curKey) + 1.0;

                if (tentative < gScore.getOrDefault(nextKey, Double.POSITIVE_INFINITY)) {
                    cameFrom.put(nextKey, cur);
                    gScore.put(nextKey, tentative);

                    double f = tentative + heuristic(next, goalN);
                    open.add(new State(next, f, tentative));
                }
            }
        }

        return null; // no valid path found
    }

    private double heuristic(Node a, Node b) {
        return euclid(a.getLng(), a.getLat(), b.getLng(), b.getLat()) / STEP;
    }

    private List<DeliveryPathDTO.LngLat> reconstruct(
            Map<String, Node> cameFrom,
            Node cur,
            Function<Node, String> key) {

        List<DeliveryPathDTO.LngLat> rev = new ArrayList<>();

        while (cur != null) {
            rev.add(new DeliveryPathDTO.LngLat(cur.getLng(), cur.getLat()));
            cur = cameFrom.get(key.apply(cur));
        }

        Collections.reverse(rev);
        return rev;
    }


    ///

    /// GeoJson
    public Map<String, Object> calcDeliveryPathAsGeoJson2(List<MedDispatchRec> dispatches) {

        if (dispatches == null || dispatches.isEmpty()) {
            throw new IllegalArgumentException("No dispatches provided");
        }

        // Fetch fresh data
        List<Drone> drones = loadMergedDrones();
        List<ServicePoint> sps = ilp.fetchServicePoints();
        List<RestrictedZone> zones = ilp.fetchRestrictedAreas();

        // Pick ONE drone that can handle ALL deliveries
        Drone chosenDrone = null;

        for (Drone d : drones) {

            if (!canHandleMultiLoad(d, dispatches)) {
                continue;
            }

            // If both OK → this drone can perform ALL deliveries in one run
            chosenDrone = d;
            break;
        }

        if (chosenDrone == null) {
            throw new RuntimeException("No single drone can handle all deliveries in one sequence");
        }

        // Start SP
        Drone finalChosenDrone = chosenDrone;
        System.out.println(finalChosenDrone.getId());
        ServicePoint sp = sps.stream()
                .filter(x -> x.getId() == finalChosenDrone.getServicePointId())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Service point not found"));

        DeliveryPathDTO.LngLat currentPos = sp.getPosition();

        // Sorting deliveries by distance (nearest-first)
        DeliveryPathDTO.LngLat finalCurrentPos = currentPos;
        dispatches.sort(Comparator.comparingDouble(
                r -> euclid(finalCurrentPos.getLng(), finalCurrentPos.getLat(),
                        r.getDelivery().getLng(), r.getDelivery().getLat())
        ));

        List<List<Double>> coords = new ArrayList<>();

        coords.add(List.of(currentPos.getLng(), currentPos.getLat()));

        // Step through all deliveries in one drone sequence
        for (MedDispatchRec rec : dispatches) {
            DeliveryPathDTO.LngLat dest =
                    new DeliveryPathDTO.LngLat(rec.getDelivery().getLng(), rec.getDelivery().getLat());

            // Compute path
            List<DeliveryPathDTO.LngLat> path =
                    calculateFlightPath(currentPos, dest, zones);

            if (path == null || path.isEmpty())
                throw new RuntimeException("No path found for delivery " + rec.getId());

            // Add all coordinates
            for (DeliveryPathDTO.LngLat p : path) {
                coords.add(List.of(p.getLng(), p.getLat()));
            }

            // remove dup at start
            coords.remove(1);
            currentPos = dest;
        }

        // return home to the service point
        List<DeliveryPathDTO.LngLat> returnPath =
                calculateFlightPath(currentPos, sp.getPosition(), zones);

        if (returnPath != null && !returnPath.isEmpty()) {
            for (DeliveryPathDTO.LngLat p : returnPath) {
                coords.add(List.of(p.getLng(), p.getLat()));
            }
        }

        // Build GeoJSON dictionary
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("type", "LineString");
        geo.put("coordinates", coords);

        return geo;
    }

    public Map<String, Object> calcDeliveryPathAsGeoJson(List<MedDispatchRec> dispatches) {

        // Graceful empty case
        if (dispatches == null || dispatches.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("type", "LineString");
            empty.put("coordinates", new ArrayList<>());
            return empty;
        }

        // Fresh data
        List<Drone> drones = loadMergedDrones();
        List<ServicePoint> sps = ilp.fetchServicePoints();
        List<RestrictedZone> zones = ilp.fetchRestrictedAreas();

        Drone chosenDrone = null;
        ServicePoint homeSp = null;

        outer:
        for (Drone d : drones) {
            ServicePoint sp = sps.stream()
                    .filter(x -> x.getId() == d.getServicePointId())
                    .findFirst()
                    .orElse(null);
            if (sp == null) continue;

            // Every dispatch must be feasible as a round-trip SP→rec→SP
            boolean ok = true;
            for (MedDispatchRec rec : dispatches) {
                if (!canHandle(d, rec)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;

            chosenDrone = d;
            homeSp = sp;
            break outer;
        }

        //  return an empty LineString
        if (chosenDrone == null || homeSp == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("type", "LineString");
            empty.put("coordinates", new ArrayList<>());
            return empty;
        }

        DeliveryPathDTO.LngLat spPos =
                new DeliveryPathDTO.LngLat(homeSp.getPosition().getLng(), homeSp.getPosition().getLat());

        List<List<Double>> coords = new ArrayList<>();
        // start at service point
        coords.add(List.of(spPos.getLng(), spPos.getLat()));

        DeliveryPathDTO.LngLat currentPos = spPos;


        for (MedDispatchRec rec : dispatches) {

            DeliveryPathDTO.LngLat dest =
                    new DeliveryPathDTO.LngLat(rec.getDelivery().getLng(), rec.getDelivery().getLat());

            // Outbound: currentPos -> dest
            List<DeliveryPathDTO.LngLat> outPath =
                    aStarPath(spPos, dest, zones);

            if (outPath == null || outPath.isEmpty()) {
                // Skip this delivery, but keep the rest; no exception
                continue;
            }

            // Append outbound, skipping the starting duplicate
            for (int i = 1; i < outPath.size(); i++) {
                DeliveryPathDTO.LngLat p = outPath.get(i);
                coords.add(List.of(p.getLng(), p.getLat()));
            }
            System.out.println("outpath="+coords);

            // Hover at destination (one duplicate point)
            List<Double> last = coords.get(coords.size() - 1);
            coords.add(last);
            System.out.println("outpath + hover="+coords);
            DeliveryPathDTO.LngLat lastPoint = new DeliveryPathDTO.LngLat(last.get(0), last.get(1));
            // Return: dest -> SP
            List<DeliveryPathDTO.LngLat> backPath =
                    aStarPath(lastPoint, spPos, zones);

            if (backPath != null && !backPath.isEmpty()) {
                for (int i = 1; i < backPath.size(); i++) {
                    DeliveryPathDTO.LngLat p = backPath.get(i);
                    coords.add(List.of(p.getLng(), p.getLat()));
                }
                currentPos = spPos; // back at base
                ///coords.add(List.of(spPos.getLng(),spPos.getLat())); if not n steps away
            } else {
                // Could not find a return path remain at dest
                currentPos = dest;
            }
        }

        // Build GeoJSON dictionary
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("type", "LineString");
        geo.put("coordinates", coords);

        return geo;
    }


    ///
    ///
    /// * CW3
    ///
    ///
    ///

    /**
     * A* pathfinding with battery consumption tracking
     * Returns null if no valid path exists that satisfies battery constraints
     */
    public PathWithBatteryResult aStarPathWithBattery(
            DeliveryPathDTO.LngLat start,
            DeliveryPathDTO.LngLat goal,
            Drone drone,
            double currentBatteryCharge,
            double payloadFraction,
            List<RestrictedZone> restrictedZones) {

        class BatteryNode {
            final DeliveryPathDTO.LngLat position;
            final double batteryRemaining;
            final double timeElapsed;  // ✅ ADD THIS
            final double gCost;
            final double fCost;

            BatteryNode(DeliveryPathDTO.LngLat pos, double battery, double time, double g, double f) {
                this.position = pos;
                this.batteryRemaining = battery;
                this.timeElapsed = time;  // ✅ ADD THIS
                this.gCost = g;
                this.fCost = f;
            }
        }

        Function<DeliveryPathDTO.LngLat, String> key = pos ->
                Math.round(pos.getLng() * 1_000_000) + ":" +
                        Math.round(pos.getLat() * 1_000_000);

        PriorityQueue<BatteryNode> openSet = new PriorityQueue<>(
                Comparator.comparingDouble(n -> n.fCost));

        Map<String, BatteryNode> bestNodes = new HashMap<>();
        Map<String, DeliveryPathDTO.LngLat> cameFrom = new HashMap<>();

        BatteryNode startNode = new BatteryNode(
                start,
                currentBatteryCharge,
                0.0,  // ✅ ADD THIS - start time is 0
                0,
                heuristic(start, goal)
        );

        String startKey = key.apply(start);
        openSet.add(startNode);
        bestNodes.put(startKey, startNode);

        BatteryModel battery = drone.getCapability().getBattery();

        // ✅ ADD THIS - Time per step calculation
        // Assume each step takes 1 second (or use cruiseSpeed if you want)
        double TIME_PER_STEP = STEP / drone.getCapability().getCruiseSpeed();


        while (!openSet.isEmpty()) {
            BatteryNode current = openSet.poll();
            String currentKey = key.apply(current.position);

            // Goal check
            if (euclid(current.position.getLng(), current.position.getLat(),
                    goal.getLng(), goal.getLat()) <= STEP) {

                List<DeliveryPathDTO.LngLat> path = reconstructPath(
                        cameFrom, current.position, key);

                return new PathWithBatteryResult(
                        path,
                        current.batteryRemaining,
                        currentBatteryCharge - current.batteryRemaining,
                        current.timeElapsed  // ✅ ADD THIS
                );
            }

            // Explore neighbors
            for (double angleDeg : ALLOWED_ANGLES) {
                double rad = Math.toRadians(angleDeg);
                DeliveryPathDTO.LngLat neighbor = new DeliveryPathDTO.LngLat(
                        current.position.getLng() + Math.cos(rad) * STEP,
                        current.position.getLat() + Math.sin(rad) * STEP
                );

                String neighborKey = key.apply(neighbor);

                if (isPointInAnyRestricted(neighbor.getLng(), neighbor.getLat(), restrictedZones)) {
                    continue;
                }

                double stepConsumption = batteryService.calculateStepConsumption(
                        battery,
                        payloadFraction
                );

                stepConsumption = batteryService.applyDegradation(
                        stepConsumption,
                        battery.getDegradationFactor(),
                        payloadFraction
                );

                double newBattery = current.batteryRemaining - stepConsumption;

                if (newBattery < 0) {
                    continue;
                }

                // ✅ ADD THIS - Calculate time for this step
                double newTime = current.timeElapsed + TIME_PER_STEP;

                double newGCost = current.gCost + 1;
                double newFCost = newGCost + heuristic(neighbor, goal);

                BatteryNode existing = bestNodes.get(neighborKey);
                if (existing == null || newGCost < existing.gCost) {
                    BatteryNode neighborNode = new BatteryNode(
                            neighbor, newBattery, newTime, newGCost, newFCost  // ✅ ADD newTime
                    );

                    bestNodes.put(neighborKey, neighborNode);
                    cameFrom.put(neighborKey, current.position);
                    openSet.add(neighborNode);
                }
            }
        }

        return null;
    }

    // Helper class for return value
    public static class PathWithBatteryResult {
        public final List<DeliveryPathDTO.LngLat> path;
        public final double batteryRemaining;
        public final double batteryUsed;
        public final double timeElapsed;  // ✅ ADD THIS (in seconds)

        public PathWithBatteryResult(List<DeliveryPathDTO.LngLat> path,
                                     double remaining,
                                     double used,
                                     double time) {  // ✅ ADD THIS
            this.path = path;
            this.batteryRemaining = remaining;
            this.batteryUsed = used;
            this.timeElapsed = time;  // ✅ ADD THIS
        }
    }

    // Reconstruct path helper (reuse your existing one or use this)
    private List<DeliveryPathDTO.LngLat> reconstructPath(
            Map<String, DeliveryPathDTO.LngLat> cameFrom,
            DeliveryPathDTO.LngLat current,
            Function<DeliveryPathDTO.LngLat, String> key) {

        List<DeliveryPathDTO.LngLat> path = new ArrayList<>();
        DeliveryPathDTO.LngLat node = current;

        while (node != null) {
            path.add(node);
            node = cameFrom.get(key.apply(node));
        }

        Collections.reverse(path);
        return path;
    }

    private double heuristic(DeliveryPathDTO.LngLat a, DeliveryPathDTO.LngLat b) {
        return euclid(a.getLng(), a.getLat(), b.getLng(), b.getLat()) / STEP;
    }


    public DeliveryPathDTO.CalcResult calcDeliveryPath(List<MedDispatchRec> dispatches) {

        if (dispatches == null) {
            dispatches = Collections.emptyList();
        }

        List<Drone> drones = loadMergedDrones();
        List<ServicePoint> sps = ilp.fetchServicePoints();
        List<RestrictedZone> zones = ilp.fetchRestrictedAreas();

        Map<Integer, ServicePoint> spById = sps.stream()
                .collect(Collectors.toMap(ServicePoint::getId, sp -> sp));

        Map<Integer, MedDispatchRec> unassigned = dispatches.stream()
                .collect(Collectors.toMap(MedDispatchRec::getId, d -> d));

        DeliveryPathDTO.CalcResult result = new DeliveryPathDTO.CalcResult();
        int totalMoves = 0;
        double totalCost = 0.0;
        double totalBatteryUsed = 0.0;  // Track this!

        Map<Integer, List<Drone>> dronesBySp =
                drones.stream().collect(Collectors.groupingBy(Drone::getServicePointId));

        for (Map.Entry<Integer, List<Drone>> spEntry : dronesBySp.entrySet()) {

            int spId = spEntry.getKey();
            ServicePoint sp = spById.get(spId);
            if (sp == null) continue;

            List<Drone> dronesAtSp = spEntry.getValue();

            for (Drone drone : dronesAtSp) {

                Capability cap = drone.getCapability();
                BatteryModel battery = cap.getBattery();

                if (battery == null) {
                    // Fallback to old behavior if no battery model
                    continue;
                }

                double currentBattery = battery.getCapacity();  // Start full

                DeliveryPathDTO.DronePath dronePath = new DeliveryPathDTO.DronePath();
                dronePath.setDroneId(drone.getId());

                while (true) {
                    if (unassigned.isEmpty()) break;

                    DeliveryPathDTO.LngLat spPos =
                            new DeliveryPathDTO.LngLat(sp.getPosition().getLng(),
                                    sp.getPosition().getLat());

                    // Find feasible deliveries
                    double finalCurrentBattery = currentBattery;
                    List<MedDispatchRec> feasible = unassigned.values().stream()
                            .filter(rec -> canHandle(drone, rec))
                            .filter(rec -> {
                                // Quick battery estimate
                                double payloadFraction = rec.getRequirements().getCapacity()
                                        / cap.getCapacity();
                                DeliveryPathDTO.LngLat dest = toLngLat(rec);

                                double estimatedSteps = estimateMoves(spPos, dest) * 2;
                                double estimatedConsumption = estimatedSteps *
                                        batteryService.calculateStepConsumption(battery, payloadFraction);

                                return finalCurrentBattery >= estimatedConsumption;
                            })
                            .collect(Collectors.toList());

                    if (feasible.isEmpty()) {
                        break;
                    }

                    // Pick nearest
                    MedDispatchRec chosen = feasible.stream()
                            .min(Comparator.comparingInt(rec -> estimateMoves(spPos, toLngLat(rec))))
                            .orElseThrow();

                    DeliveryPathDTO.LngLat dest = toLngLat(chosen);
                    double payloadFraction = chosen.getRequirements().getCapacity()
                            / cap.getCapacity();

                    // Outbound with battery tracking
                    PathWithBatteryResult outPath = aStarPathWithBattery(
                            spPos, dest, drone, currentBattery, payloadFraction, zones);

                    if (outPath == null) {
                        unassigned.remove(chosen.getId());
                        continue;
                    }

                    List<DeliveryPathDTO.LngLat> fullPath = new ArrayList<>(outPath.path);
                    currentBattery = outPath.batteryRemaining;

                    // Hover
                    DeliveryPathDTO.LngLat last = fullPath.get(fullPath.size() - 1);
                    fullPath.add(last);  // hover duplicate

                    // Return path (empty drone, so payloadFraction = 0)
                    PathWithBatteryResult backPath = aStarPathWithBattery(
                            last, spPos, drone, currentBattery, 0.0, zones);

                    if (backPath != null) {
                        for (int i = 1; i < backPath.path.size(); i++) {
                            fullPath.add(backPath.path.get(i));
                        }
                        currentBattery = backPath.batteryRemaining;
                    }

                    int used = fullPath.size();
                    totalMoves += used;
                    totalBatteryUsed += (outPath.batteryUsed +
                            (backPath != null ? backPath.batteryUsed : 0));

                    DeliveryPathDTO.DeliveryPath dp = new DeliveryPathDTO.DeliveryPath();
                    dp.setDeliveryId(chosen.getId());
                    dp.setFlightPath(fullPath);
                    dronePath.getDeliveries().add(dp);

                    unassigned.remove(chosen.getId());

                    // Recharge for next delivery
                    currentBattery = battery.getCapacity();
                }

                if (!dronePath.getDeliveries().isEmpty()) {
                    // Calculate cost (you can add battery cost here if needed)
                    double droneCost = cap.getCostInitial() +
                            totalMoves * cap.getCostPerMove() +
                            cap.getCostFinal();
                    totalCost += droneCost;

                    result.getDronePaths().add(dronePath);
                }

                if (unassigned.isEmpty()) break;
            }

            if (unassigned.isEmpty()) break;
        }

        result.setTotalMoves(totalMoves);
        result.setTotalCost(totalCost);
        return result;
    }




    /// scheduling
    ///
    /// helper functions
    ///
    public static class EnhancedPlanResult {
        public List<EnhancedDroneMission> missions = new ArrayList<>();

        public double getTotalFlightTimeMinutes() {
            return missions.stream()
                    .mapToDouble(m -> m.getTotalFlightTimeMinutes())
                    .sum();
        }

        public double getTotalBatteryUsed() {
            return missions.stream()
                    .mapToDouble(m -> m.getTotalBatteryUsed())
                    .sum();
        }

        public int getTotalMoves() {
            return missions.stream()
                    .mapToInt(m -> m.getTotalMoves())
                    .sum();
        }
    }

    public static class EnhancedDroneMission {
        public String droneId;
        public List<EnhancedDeliveryLeg> legs = new ArrayList<>();

        public double getTotalFlightTimeMinutes() {
            return legs.stream()
                    .mapToDouble(l -> l.flightDurationSeconds / 60.0)
                    .sum();
        }

        public double getTotalRechargeTimeMinutes() {
            return legs.stream()
                    .mapToDouble(l -> l.rechargeTimeMinutes)
                    .sum();
        }

        public double getTotalBatteryUsed() {
            return legs.stream()
                    .mapToDouble(l -> l.batteryUsed)
                    .sum();
        }

        public int getTotalMoves() {
            return legs.stream()
                    .mapToInt(l -> l.flightPath.size())
                    .sum();
        }

        public double getTotalTimeMinutes() {
            if (legs.isEmpty()) return 0;
            LocalDateTime start = legs.get(0).departureTime;
            LocalDateTime end = legs.get(legs.size() - 1).returnTime; // ✅ Include recharge
            return Duration.between(start, end).toMinutes();
        }
    }

    public static class EnhancedDeliveryLeg {
        public int deliveryId;
        public LocalDateTime departureTime;
        public LocalDateTime arrivalTime;
        public LocalDateTime returnTime;
        public double batteryUsed;
        public double batteryLevelBefore;    // ✅ NEW: Battery before charging for this delivery
        public double batteryLevelAfter;     // Battery after this delivery
        public double flightDurationSeconds;
        public List<DeliveryPathDTO.LngLat> flightPath;
        public double queueDelayMinutes;
        public double rechargeTimeMinutes;   // Time spent charging BEFORE this delivery
    }

    //main logic
    public EnhancedPlanResult calcDeliveryPathWithScheduling1(List<MedDispatchRec> dispatches) {

        if (dispatches == null || dispatches.isEmpty()) {
            return new EnhancedPlanResult();
        }

        List<Drone> drones = loadMergedDrones();
        List<ServicePoint> sps = ilp.fetchServicePoints();
        List<RestrictedZone> zones = ilp.fetchRestrictedAreas();

        Map<Integer, ServicePoint> spById = sps.stream()
                .collect(Collectors.toMap(ServicePoint::getId, sp -> sp));

        Map<Integer, MedDispatchRec> unassigned = dispatches.stream()
                .collect(Collectors.toMap(MedDispatchRec::getId, d -> d));

        EnhancedPlanResult result = new EnhancedPlanResult();

        Map<String, LocalDateTime> droneCurrentTime = new HashMap<>();
        Map<String, Double> droneBattery = new HashMap<>();

        // ✅ FIX: Initialize drones at their earliest availability time
        for (Drone d : drones) {
            LocalDate firstDispatchDate = dispatches.get(0).getDate();
            LocalDateTime earliest = getEarliestAvailability(d, firstDispatchDate);
            droneCurrentTime.put(d.getId(), earliest);

            BatteryModel bm = d.getCapability().getBattery();
            droneBattery.put(d.getId(), bm.getCurrentCharge());
        }

        Map<Integer, List<Drone>> dronesBySp =
                drones.stream().collect(Collectors.groupingBy(Drone::getServicePointId));

        // ✅ FIX: Keep trying until all deliveries assigned or no drone can handle remaining
        int maxRounds = 100; // Safety limit
        int round = 0;

        while (!unassigned.isEmpty() && round < maxRounds) {
            round++;
            boolean assignedAny = false;

            System.out.println("\n=== ROUND " + round + " - " + unassigned.size() + " deliveries remaining ===");

            for (Map.Entry<Integer, List<Drone>> spEntry : dronesBySp.entrySet()) {

                int spId = spEntry.getKey();
                ServicePoint sp = spById.get(spId);
                if (sp == null) continue;

                List<Drone> dronesAtSp = spEntry.getValue();

                for (Drone drone : dronesAtSp) {

                    Capability cap = drone.getCapability();
                    BatteryModel battery = cap.getBattery();
                    if (battery == null) continue;

                    double currentBattery = droneBattery.get(drone.getId());
                    LocalDateTime currentTime = droneCurrentTime.get(drone.getId());
                    double rechargeRatePerMinute = sp.getRechargeRate();

                    System.out.println("\n--- DRONE " + drone.getId() + " ---");
                    System.out.println("Current battery: " + String.format("%.2f", currentBattery) + " Wh");
                    System.out.println("Current time: " + currentTime);

                    if (unassigned.isEmpty()) break;

                    DeliveryPathDTO.LngLat spPos =
                            new DeliveryPathDTO.LngLat(sp.getPosition().getLng(),
                                    sp.getPosition().getLat());

                    // ✅ FIX: Apply passive charging from current time to earliest dispatch time
                    LocalDateTime earliestDispatchTime = unassigned.values().stream()
                            .map(r -> LocalDateTime.of(r.getDate(), r.getTime()))
                            .min(LocalDateTime::compareTo)
                            .orElse(currentTime);

                    if (earliestDispatchTime.isAfter(currentTime) && currentBattery < battery.getCapacity()) {
                        long idleMinutes = Duration.between(currentTime, earliestDispatchTime).toMinutes();

                        if (idleMinutes > 0) {
                            double chargeGained = Math.min(
                                    idleMinutes * rechargeRatePerMinute,
                                    battery.getCapacity() - currentBattery
                            );

                            double batteryBefore = currentBattery;
                            currentBattery += chargeGained;
                            currentTime = earliestDispatchTime;

                            System.out.println("⚡ PASSIVE CHARGE: " +
                                    String.format("%.2f", batteryBefore) +
                                    " → " + String.format("%.2f", currentBattery) +
                                    " Wh (idle for " + idleMinutes + " min until " + earliestDispatchTime + ")");
                        }
                    }

                    double finalCurrentBattery = currentBattery;
                    LocalDateTime finalCurrentTime = currentTime;

                    List<MedDispatchRec> feasible = unassigned.values().stream()
                            .filter(rec -> {

                                if (!canHandle(drone, rec)) {
                                    return false;
                                }

                                // ✅ Check if dispatch time has arrived
                                LocalDateTime dispatchTime = LocalDateTime.of(rec.getDate(), rec.getTime());
                                if (finalCurrentTime.isBefore(dispatchTime)) {
                                    // Not time yet for this dispatch
                                    return false;
                                }

                                double payloadFraction = rec.getRequirements().getCapacity()
                                        / cap.getCapacity();
                                DeliveryPathDTO.LngLat dest = toLngLat(rec);

                                double estimatedSteps = estimateMoves(spPos, dest) * 2;
                                double estimatedConsumption = estimatedSteps *
                                        batteryService.calculateStepConsumption(battery, payloadFraction);

                                estimatedConsumption *= 1.10;

                                System.out.println("  ? Delivery " + rec.getId() +
                                        " (at " + dispatchTime + ") needs ~" +
                                        String.format("%.2f", estimatedConsumption) +
                                        " Wh, have " + String.format("%.2f", finalCurrentBattery) + " Wh");

                                // ✅ Can we charge enough in time?
                                if (finalCurrentBattery < estimatedConsumption) {
                                    double deficit = estimatedConsumption - finalCurrentBattery;
                                    double chargeTimeNeeded = deficit / rechargeRatePerMinute;

                                    // Check if we can still charge more (not at max capacity)
                                    if (finalCurrentBattery >= battery.getCapacity()) {
                                        System.out.println("    ✗ Battery full but still insufficient");
                                        return false;
                                    }

                                    System.out.println("    → Need " + String.format("%.2f", chargeTimeNeeded) +
                                            " more min of charging");

                                    // We can charge and then do this delivery
                                    return true;
                                }

                                double queueDelay = schedulingService.calculateQueueDelay(
                                        sp, finalCurrentTime, 0);

                                double roughFlightOut = (estimateMoves(spPos, dest) * 1.0) / 60.0;
                                double roughFlightBack = roughFlightOut;

                                double chargeTimeNeeded = 0;
                                if (finalCurrentBattery < estimatedConsumption) {
                                    double deficit = estimatedConsumption - finalCurrentBattery;
                                    chargeTimeNeeded = deficit / rechargeRatePerMinute;
                                }

                                double totalExpectedMinutes = queueDelay +
                                        chargeTimeNeeded +
                                        roughFlightOut +
                                        roughFlightBack;

                                boolean fitsAvailability = schedulingService.fitsInAvailability(
                                        drone, finalCurrentTime, totalExpectedMinutes);

                                if (!fitsAvailability) {
                                    System.out.println("    ✗ Doesn't fit availability window");
                                }

                                return fitsAvailability;
                            })
                            .collect(Collectors.toList());

                    if (feasible.isEmpty()) {
                        System.out.println("No feasible deliveries for this drone right now.");
                        continue; // Try next drone
                    }

                    // Find mission for this drone or create new one
                    EnhancedDroneMission mission = result.missions.stream()
                            .filter(m -> m.droneId.equals(drone.getId()))
                            .findFirst()
                            .orElseGet(() -> {
                                EnhancedDroneMission m = new EnhancedDroneMission();
                                m.droneId = drone.getId();
                                result.missions.add(m);
                                return m;
                            });

                    MedDispatchRec chosen = feasible.stream()
                            .min(Comparator.comparingInt(rec -> estimateMoves(spPos, toLngLat(rec))))
                            .orElseThrow();

                    System.out.println("✓ Selected delivery " + chosen.getId());

                    DeliveryPathDTO.LngLat dest = toLngLat(chosen);
                    double payloadFraction = chosen.getRequirements().getCapacity()
                            / cap.getCapacity();

                    double estimatedSteps = estimateMoves(spPos, dest) * 2;
                    double estimatedConsumption = estimatedSteps *
                            batteryService.calculateStepConsumption(battery, payloadFraction);
                    estimatedConsumption *= 1.10;

                    // Active charging if needed
                    double chargeTimeMinutes = 0;
                    double batteryBeforeActiveCharge = currentBattery;

                    if (currentBattery < estimatedConsumption) {
                        double deficit = estimatedConsumption - currentBattery;
                        chargeTimeMinutes = deficit / rechargeRatePerMinute;

                        currentTime = currentTime.plusMinutes((long) Math.ceil(chargeTimeMinutes));
                        currentBattery += chargeTimeMinutes * rechargeRatePerMinute;
                        currentBattery = Math.min(currentBattery, battery.getCapacity());

                        System.out.println("🔋 ACTIVE CHARGE: " +
                                String.format("%.2f", batteryBeforeActiveCharge) +
                                " → " + String.format("%.2f", currentBattery) +
                                " Wh (took " + String.format("%.2f", chargeTimeMinutes) + " min)");
                    }

                    double queueDelay = schedulingService.calculateQueueDelay(
                            sp, currentTime, 0);

                    if (queueDelay > 0) {
                        System.out.println("⏳ QUEUE: Waiting " + queueDelay + " minutes");
                    }

                    LocalDateTime actualDepartureTime = currentTime.plusMinutes((long) queueDelay);

                    System.out.println("✈️  DEPARTING: at " + actualDepartureTime +
                            " with battery " + String.format("%.2f", currentBattery) + " Wh");

                    PathWithBatteryResult outPath = aStarPathWithBattery(
                            spPos, dest, drone, currentBattery, payloadFraction, zones);

                    if (outPath == null) {
                        System.out.println("✗ A* failed to find path");
                        unassigned.remove(chosen.getId());
                        continue;
                    }

                    currentBattery = outPath.batteryRemaining;
                    LocalDateTime arrivalTime = actualDepartureTime.plusSeconds((long) outPath.timeElapsed);

                    List<DeliveryPathDTO.LngLat> fullPath = new ArrayList<>(outPath.path);
                    DeliveryPathDTO.LngLat last = fullPath.get(fullPath.size() - 1);
                    fullPath.add(last);

                    PathWithBatteryResult backPath = aStarPathWithBattery(
                            last, spPos, drone, currentBattery, 0.0, zones);

                    if (backPath == null) {
                        System.out.println("✗ A* failed to find return path");
                        unassigned.remove(chosen.getId());
                        continue;
                    }

                    for (int i = 1; i < backPath.path.size(); i++) {
                        fullPath.add(backPath.path.get(i));
                    }

                    currentBattery = backPath.batteryRemaining;
                    currentTime = arrivalTime.plusSeconds((long) backPath.timeElapsed);

                    System.out.println("🏠 RETURNED: at " + currentTime +
                            " with battery " + String.format("%.2f", currentBattery) + " Wh");

                    schedulingService.reserveSlot(
                            sp.getId(),
                            actualDepartureTime,
                            currentTime,
                            drone.getId()
                    );

                    EnhancedDeliveryLeg leg = new EnhancedDeliveryLeg();
                    leg.deliveryId = chosen.getId();
                    leg.departureTime = actualDepartureTime;
                    leg.arrivalTime = arrivalTime;
                    leg.returnTime = currentTime;
                    leg.batteryUsed = outPath.batteryUsed + backPath.batteryUsed;
                    leg.flightPath = fullPath;
                    leg.queueDelayMinutes = queueDelay;
                    leg.flightDurationSeconds = outPath.timeElapsed + backPath.timeElapsed;
                    leg.rechargeTimeMinutes = chargeTimeMinutes;
                    leg.batteryLevelAfter = currentBattery;
                    leg.batteryLevelBefore = batteryBeforeActiveCharge;

                    mission.legs.add(leg);
                    unassigned.remove(chosen.getId());
                    assignedAny = true;

                    droneCurrentTime.put(drone.getId(), currentTime);
                    droneBattery.put(drone.getId(), currentBattery);

                    if (unassigned.isEmpty()) break;
                }

                if (unassigned.isEmpty()) break;
            }

            // ✅ If no drone could take any delivery this round, advance time and let them charge
            if (!assignedAny && !unassigned.isEmpty()) {
                System.out.println("\n⚠️  No drone ready yet - advancing time by 1 minute for charging...");

                for (Drone d : drones) {
                    LocalDateTime currentTime = droneCurrentTime.get(d.getId());
                    double currentBattery = droneBattery.get(d.getId());

                    ServicePoint sp = spById.get(d.getServicePointId());
                    if (sp != null && currentBattery < d.getCapability().getBattery().getCapacity()) {
                        currentBattery += sp.getRechargeRate() / 60.0; // 1 minute of charging
                        currentBattery = Math.min(currentBattery, d.getCapability().getBattery().getCapacity());

                        droneBattery.put(d.getId(), currentBattery);
                        droneCurrentTime.put(d.getId(), currentTime.plusMinutes(1));
                    }
                }
            }

            if (unassigned.isEmpty()) break;
        }

        if (!unassigned.isEmpty()) {
            System.out.println("\n❌ WARNING: " + unassigned.size() + " deliveries could not be assigned!");
            unassigned.values().forEach(rec ->
                    System.out.println("  - Delivery " + rec.getId() + " at " + rec.getTime()));
        }

        return result;
    }

    public EnhancedPlanResult calcDeliveryPathWithScheduling(List<MedDispatchRec> dispatches) {

        if (dispatches == null || dispatches.isEmpty()) {
            return new EnhancedPlanResult();
        }

        List<Drone> drones = loadMergedDrones();
        List<ServicePoint> sps = ilp.fetchServicePoints();
        List<RestrictedZone> zones = ilp.fetchRestrictedAreas();

        Map<Integer, ServicePoint> spById = sps.stream()
                .collect(Collectors.toMap(ServicePoint::getId, sp -> sp));

        Map<Integer, MedDispatchRec> unassigned = dispatches.stream()
                .collect(Collectors.toMap(MedDispatchRec::getId, d -> d));

        EnhancedPlanResult result = new EnhancedPlanResult();

        Map<String, LocalDateTime> droneCurrentTime = new HashMap<>();
        Map<String, Double> droneBattery = new HashMap<>();

        // ✅ FIX: Only initialize drones that are actually available on dispatch dates
        LocalDate firstDispatchDate = dispatches.get(0).getDate();

        for (Drone d : drones) {
            // Check if drone has ANY availability on the first dispatch date
            boolean availableOnDate = d.getAvailability().stream()
                    .anyMatch(av -> av.getDayOfWeek() == firstDispatchDate.getDayOfWeek());

            if (!availableOnDate) {
                // Skip this drone - not available on dispatch day
                System.out.println("⊗ Drone " + d.getId() + " not available on " +
                        firstDispatchDate.getDayOfWeek());
                continue;
            }

            LocalDateTime earliest = getEarliestAvailability(d, firstDispatchDate);
            droneCurrentTime.put(d.getId(), earliest);

            BatteryModel bm = d.getCapability().getBattery();
            if (bm != null) {
                droneBattery.put(d.getId(), bm.getCurrentCharge());

                System.out.println("✓ Drone " + d.getId() + " available at " + earliest +
                        " with " + String.format("%.2f", bm.getCurrentCharge()) + " Wh");
            }
        }

        // ✅ Filter drones to only those that were initialized
        drones = drones.stream()
                .filter(d -> droneCurrentTime.containsKey(d.getId()))
                .collect(Collectors.toList());

        if (drones.isEmpty()) {
            System.out.println("❌ No drones available on " + firstDispatchDate);
            return result;
        }

        Map<Integer, List<Drone>> dronesBySp =
                drones.stream().collect(Collectors.groupingBy(Drone::getServicePointId));

        int maxRounds = 100;
        int round = 0;

        while (!unassigned.isEmpty() && round < maxRounds) {
            round++;
            boolean assignedAny = false;

            System.out.println("\n=== ROUND " + round + " - " + unassigned.size() +
                    " deliveries remaining, " + drones.size() + " drones active ===");

            for (Map.Entry<Integer, List<Drone>> spEntry : dronesBySp.entrySet()) {

                int spId = spEntry.getKey();
                ServicePoint sp = spById.get(spId);
                if (sp == null) continue;

                List<Drone> dronesAtSp = spEntry.getValue();

                for (Drone drone : dronesAtSp) {

                    Capability cap = drone.getCapability();
                    BatteryModel battery = cap.getBattery();
                    if (battery == null) continue;

                    double currentBattery = droneBattery.get(drone.getId());
                    LocalDateTime currentTime = droneCurrentTime.get(drone.getId());
                    double rechargeRatePerMinute = sp.getRechargeRate();

                    System.out.println("\n--- DRONE " + drone.getId() + " ---");
                    System.out.println("Current battery: " + String.format("%.2f", currentBattery) + " Wh");
                    System.out.println("Current time: " + currentTime);

                    if (unassigned.isEmpty()) break;

                    DeliveryPathDTO.LngLat spPos =
                            new DeliveryPathDTO.LngLat(sp.getPosition().getLng(),
                                    sp.getPosition().getLat());

                    // Passive charging
                    LocalDateTime finalCurrentTime1 = currentTime;
                    LocalDateTime earliestDispatchTime = unassigned.values().stream()
                            .map(r -> LocalDateTime.of(r.getDate(), r.getTime()))
                            .filter(dt -> !dt.isBefore(finalCurrentTime1)) // ✅ Only future dispatches
                            .min(LocalDateTime::compareTo)
                            .orElse(currentTime);

                    if (earliestDispatchTime.isAfter(currentTime) && currentBattery < battery.getCapacity()) {
                        long idleMinutes = Duration.between(currentTime, earliestDispatchTime).toMinutes();

                        if (idleMinutes > 0) {
                            double chargeGained = Math.min(
                                    idleMinutes * rechargeRatePerMinute,
                                    battery.getCapacity() - currentBattery
                            );

                            double batteryBefore = currentBattery;
                            currentBattery += chargeGained;
                            currentTime = earliestDispatchTime;

                            System.out.println("⚡ PASSIVE CHARGE: " +
                                    String.format("%.2f", batteryBefore) +
                                    " → " + String.format("%.2f", currentBattery) +
                                    " Wh (idle for " + idleMinutes + " min until " + earliestDispatchTime + ")");
                        }
                    }

                    double finalCurrentBattery = currentBattery;
                    LocalDateTime finalCurrentTime = currentTime;

                    List<MedDispatchRec> feasible = unassigned.values().stream()
                            .filter(rec -> {

                                if (!canHandle(drone, rec)) {
                                    return false;
                                }

                                LocalDateTime dispatchTime = LocalDateTime.of(rec.getDate(), rec.getTime());
                                if (finalCurrentTime.isBefore(dispatchTime)) {
                                    return false;
                                }

                                double payloadFraction = rec.getRequirements().getCapacity()
                                        / cap.getCapacity();
                                DeliveryPathDTO.LngLat dest = toLngLat(rec);

                                double estimatedSteps = estimateMoves(spPos, dest) * 2;
                                double estimatedConsumption = estimatedSteps *
                                        batteryService.calculateStepConsumption(battery, payloadFraction);

                                estimatedConsumption *= 1.10;

                                System.out.println("  ? Delivery " + rec.getId() +
                                        " (at " + dispatchTime + ") needs ~" +
                                        String.format("%.2f", estimatedConsumption) +
                                        " Wh, have " + String.format("%.2f", finalCurrentBattery) + " Wh");

                                if (finalCurrentBattery < estimatedConsumption) {
                                    double deficit = estimatedConsumption - finalCurrentBattery;
                                    double chargeTimeNeeded = deficit / rechargeRatePerMinute;

                                    if (finalCurrentBattery >= battery.getCapacity()) {
                                        System.out.println("    ✗ Battery full but still insufficient");
                                        return false;
                                    }

                                    System.out.println("    → Need " + String.format("%.2f", chargeTimeNeeded) +
                                            " more min of charging");

                                    return true;
                                }

                                double queueDelay = schedulingService.calculateQueueDelay(
                                        sp, finalCurrentTime, 0);

                                double roughFlightOut = (estimateMoves(spPos, dest) * 1.0) / 60.0;
                                double roughFlightBack = roughFlightOut;

                                double chargeTimeNeeded = 0;
                                if (finalCurrentBattery < estimatedConsumption) {
                                    double deficit = estimatedConsumption - finalCurrentBattery;
                                    chargeTimeNeeded = deficit / rechargeRatePerMinute;
                                }

                                double totalExpectedMinutes = queueDelay +
                                        chargeTimeNeeded +
                                        roughFlightOut +
                                        roughFlightBack;

                                boolean fitsAvailability = schedulingService.fitsInAvailability(
                                        drone, finalCurrentTime, totalExpectedMinutes);

                                if (!fitsAvailability) {
                                    System.out.println("    ✗ Doesn't fit availability window");
                                }

                                return fitsAvailability;
                            })
                            .collect(Collectors.toList());

                    if (feasible.isEmpty()) {
                        System.out.println("No feasible deliveries for this drone right now.");
                        continue;
                    }

                    EnhancedDroneMission mission = result.missions.stream()
                            .filter(m -> m.droneId.equals(drone.getId()))
                            .findFirst()
                            .orElseGet(() -> {
                                EnhancedDroneMission m = new EnhancedDroneMission();
                                m.droneId = drone.getId();
                                result.missions.add(m);
                                return m;
                            });

                    MedDispatchRec chosen = feasible.stream()
                            .min(Comparator.comparingInt(rec -> estimateMoves(spPos, toLngLat(rec))))
                            .orElseThrow();

                    System.out.println("✓ Selected delivery " + chosen.getId());

                    DeliveryPathDTO.LngLat dest = toLngLat(chosen);
                    double payloadFraction = chosen.getRequirements().getCapacity()
                            / cap.getCapacity();

                    double estimatedSteps = estimateMoves(spPos, dest) * 2;
                    double estimatedConsumption = estimatedSteps *
                            batteryService.calculateStepConsumption(battery, payloadFraction);
                    estimatedConsumption *= 1.10;

                    double chargeTimeMinutes = 0;
                    double batteryBeforeActiveCharge = currentBattery;

                    if (currentBattery < estimatedConsumption) {
                        double deficit = estimatedConsumption - currentBattery;
                        chargeTimeMinutes = deficit / rechargeRatePerMinute;

                        currentTime = currentTime.plusMinutes((long) Math.ceil(chargeTimeMinutes));
                        currentBattery += chargeTimeMinutes * rechargeRatePerMinute;
                        currentBattery = Math.min(currentBattery, battery.getCapacity());

                        System.out.println("🔋 ACTIVE CHARGE: " +
                                String.format("%.2f", batteryBeforeActiveCharge) +
                                " → " + String.format("%.2f", currentBattery) +
                                " Wh (took " + String.format("%.2f", chargeTimeMinutes) + " min)");
                    }

                    double queueDelay = schedulingService.calculateQueueDelay(
                            sp, currentTime, 0);

                    if (queueDelay > 0) {
                        System.out.println("⏳ QUEUE: Waiting " + queueDelay + " minutes");
                    }

                    LocalDateTime actualDepartureTime = currentTime.plusMinutes((long) queueDelay);

                    System.out.println("✈️  DEPARTING: at " + actualDepartureTime +
                            " with battery " + String.format("%.2f", currentBattery) + " Wh");

                    PathWithBatteryResult outPath = aStarPathWithBattery(
                            spPos, dest, drone, currentBattery, payloadFraction, zones);

                    if (outPath == null) {
                        System.out.println("✗ A* failed to find path");
                        unassigned.remove(chosen.getId());
                        continue;
                    }

                    currentBattery = outPath.batteryRemaining;
                    LocalDateTime arrivalTime = actualDepartureTime.plusSeconds((long) outPath.timeElapsed);

                    List<DeliveryPathDTO.LngLat> fullPath = new ArrayList<>(outPath.path);
                    DeliveryPathDTO.LngLat last = fullPath.get(fullPath.size() - 1);
                    fullPath.add(last);

                    PathWithBatteryResult backPath = aStarPathWithBattery(
                            last, spPos, drone, currentBattery, 0.0, zones);

                    if (backPath == null) {
                        System.out.println("✗ A* failed to find return path");
                        unassigned.remove(chosen.getId());
                        continue;
                    }

                    for (int i = 1; i < backPath.path.size(); i++) {
                        fullPath.add(backPath.path.get(i));
                    }

                    currentBattery = backPath.batteryRemaining;
                    currentTime = arrivalTime.plusSeconds((long) backPath.timeElapsed);

                    System.out.println("🏠 RETURNED: at " + currentTime +
                            " with battery " + String.format("%.2f", currentBattery) + " Wh");

                    schedulingService.reserveSlot(
                            sp.getId(),
                            actualDepartureTime,
                            currentTime,
                            drone.getId()
                    );

                    EnhancedDeliveryLeg leg = new EnhancedDeliveryLeg();
                    leg.deliveryId = chosen.getId();
                    leg.departureTime = actualDepartureTime;
                    leg.arrivalTime = arrivalTime;
                    leg.returnTime = currentTime;
                    leg.batteryUsed = outPath.batteryUsed + backPath.batteryUsed;
                    leg.flightPath = fullPath;
                    leg.queueDelayMinutes = queueDelay;
                    leg.flightDurationSeconds = outPath.timeElapsed + backPath.timeElapsed;
                    leg.rechargeTimeMinutes = chargeTimeMinutes;
                    leg.batteryLevelAfter = currentBattery;
                    leg.batteryLevelBefore = batteryBeforeActiveCharge;

                    mission.legs.add(leg);
                    unassigned.remove(chosen.getId());
                    assignedAny = true;

                    droneCurrentTime.put(drone.getId(), currentTime);
                    droneBattery.put(drone.getId(), currentBattery);

                    if (unassigned.isEmpty()) break;
                }

                if (unassigned.isEmpty()) break;
            }

            if (!assignedAny && !unassigned.isEmpty()) {
                System.out.println("\n⚠️  No drone ready yet - advancing time by 1 minute for charging...");

                for (Drone d : drones) {
                    LocalDateTime currentTime = droneCurrentTime.get(d.getId());
                    double currentBattery = droneBattery.get(d.getId());

                    ServicePoint sp = spById.get(d.getServicePointId());
                    if (sp != null && currentBattery < d.getCapability().getBattery().getCapacity()) {
                        currentBattery += sp.getRechargeRate() / 60.0;
                        currentBattery = Math.min(currentBattery, d.getCapability().getBattery().getCapacity());

                        droneBattery.put(d.getId(), currentBattery);
                        droneCurrentTime.put(d.getId(), currentTime.plusMinutes(1));
                    }
                }
            }

            if (unassigned.isEmpty()) break;
        }

        if (!unassigned.isEmpty()) {
            System.out.println("\n❌ WARNING: " + unassigned.size() + " deliveries could not be assigned!");
            unassigned.values().forEach(rec ->
                    System.out.println("  - Delivery " + rec.getId() + " at " + rec.getTime()));
        }

        return result;
    }

    // ✅ NEW METHOD: Consider both availability AND dispatch time
    private LocalDateTime getEarliestAvailabilityForDispatch(Drone drone, MedDispatchRec dispatch) {
        LocalDate date = dispatch.getDate();
        LocalTime requestedTime = dispatch.getTime();
        LocalDateTime requestedDateTime = LocalDateTime.of(date, requestedTime);

        // Find availability windows for this day
        List<LocalDateTime> availabilityStarts = drone.getAvailability().stream()
                .filter(av -> av.getDayOfWeek() == date.getDayOfWeek())
                .map(av -> LocalDateTime.of(date, av.getFrom()))
                .collect(Collectors.toList());

        if (availabilityStarts.isEmpty()) {
            // No availability this day - return requested time anyway (will fail later)
            return requestedDateTime;
        }

        // Return the LATER of: earliest availability OR requested dispatch time
        LocalDateTime earliestAvailability = availabilityStarts.stream()
                .min(LocalDateTime::compareTo)
                .orElse(requestedDateTime);

        // Can't start before availability opens
        // But also respect the requested dispatch time if it's later
        return earliestAvailability.isAfter(requestedDateTime)
                ? earliestAvailability
                : requestedDateTime;
    }

    private LocalDateTime getEarliestAvailability(Drone drone, LocalDate date) {
        return drone.getAvailability().stream()
                .filter(av -> av.getDayOfWeek() == date.getDayOfWeek())
                .map(av -> LocalDateTime.of(date, av.getFrom()))
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.of(date, LocalTime.of(9, 0)));
    }

    public ResponseEntity<String> testing(){

        Drone drone = new Drone();
        drone.setId("test-drone");

        Capability cap = new Capability();
        cap.setCapacity(10.0);  // 10kg cargo

        BatteryModel battery = new BatteryModel();
        battery.setCapacity(100.0);  // 100 Wh
        battery.setBaseConsumptionPerStep(0.5);
        battery.setConsumptionPayloadFactor(0.2);
        battery.setDegradationFactor(0.1);
        battery.setCurrentCharge(100.0);

        cap.setBattery(battery);
        drone.setCapability(cap);

        // Test path
        DeliveryPathDTO.LngLat start = new DeliveryPathDTO.LngLat(-3.186, 55.944);
        DeliveryPathDTO.LngLat goal = new DeliveryPathDTO.LngLat(-3.188, 55.946);

        PathWithBatteryResult result = aStarPathWithBattery(
                start, goal, drone, 100.0, 0.5,
                fetchRestrictedAreas());

        if (result != null) {
            return ResponseEntity.ok(
                    "Path found! Steps: " + result.path.size() +
                            ", Battery used: " + result.batteryUsed + " Wh" +
                            ", Battery remaining: " + result.batteryRemaining + " Wh"
            );
        } else {
            return ResponseEntity.ok("No path found (battery insufficient)");
        }
    }

    public ResponseEntity<String> testScheduler() {

        StringBuilder report = new StringBuilder();
        report.append("=== Scheduler Test Results ===\n\n");

        // -----------------------------------------
        // 1) A* sanity test
        // -----------------------------------------
        Drone d1 = new Drone();
        d1.setId("Astar-Drone");
        Capability cap = new Capability();
        BatteryModel bm = new BatteryModel();
        bm.setCapacity(100);
        bm.setBaseConsumptionPerStep(0.2);
        bm.setConsumptionPayloadFactor(0.1);
        bm.setDegradationFactor(0.05);
        bm.setCurrentCharge(100);
        cap.setBattery(bm);
        cap.setCruiseSpeed(0.00015); // lnglat
        d1.setCapability(cap);

        DeliveryPathDTO.LngLat pStart = new DeliveryPathDTO.LngLat(-3.1863580788986368, 55.94468066708487);//apppleton
        DeliveryPathDTO.LngLat pEnd   = new DeliveryPathDTO.LngLat(-3.191, 55.943); //opp gs

        PathWithBatteryResult pathRes = aStarPathWithBattery(pStart, pEnd, d1, 100, 0.5, fetchRestrictedAreas());

        report.append("[Test 1: A*]\n");
        if (pathRes != null)
            report.append("✔ Path steps: ").append(pathRes.path.size())
                    .append(" | Battery used: ").append(pathRes.batteryUsed)
                    .append(" | Time: ").append(pathRes.timeElapsed).append(" s\n\n");
        else
            report.append("✘ A* failed unexpectedly\n\n");


        // -----------------------------------------
        // 2) Continuous charging + interrupt test
        // -----------------------------------------
        Drone d2 = cloneDrone(d1);
        d2.setId("ChargingDrone");

        // Set battery to 40% of capacity
        d2.getCapability().getBattery().setCurrentCharge(40);

        // Recharge rate: 5 Wh per minute
        ServicePoint sp = new ServicePoint();
        sp.setId(1);
        sp.setRechargeRate(5.0);

        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 9, 0);
        double requiredEnergy = 60; // delivery needs 60 Wh

        // Simulate 3 minutes idle charging (3*5 = 15 Wh)
        double currBat = d2.getCapability().getBattery().getCurrentCharge();
        currBat += 3 * sp.getRechargeRate();
        d2.getCapability().getBattery().setCurrentCharge(currBat);

        report.append("[Test 2: Continuous Charging]\n");
        report.append("Initial battery 40 Wh → after 3 min idle = ")
                .append(d2.getCapability().getBattery().getCurrentCharge()).append(" Wh\n");

        boolean enoughNow = d2.getCapability().getBattery().getCurrentCharge() >= requiredEnergy;
        report.append("Enough to fly (>=60Wh)? ").append(enoughNow ? "✔ Yes" : "✘ No").append("\n\n");


        // -----------------------------------------
        // 3) Full schedule test with 1 drone
        // -----------------------------------------
        MedDispatchRec rec = new MedDispatchRec();
        rec.setId(101);
        rec.setDate(LocalDate.of(2025, 1, 1));
        rec.setTime(LocalTime.parse("10:00"));

        MedDispatchRec.Requirements req = new MedDispatchRec.Requirements();
        req.setCapacity(5.0);
        req.setCooling(false);
        req.setHeating(false);
        req.setMaxCost(999.0);   // or null if not needed
        rec.setRequirements(req);

        MedDispatchRec.Delivery delivery = new MedDispatchRec.Delivery();
        delivery.setLng(-3.187);
        delivery.setLat(55.945);
        rec.setDelivery(delivery);

        List<MedDispatchRec> jobs = Arrays.asList(rec);

        EnhancedPlanResult plan = calcDeliveryPathWithScheduling(jobs);

        report.append("[Test 3: Full Scheduling]\n");
        if (!plan.missions.isEmpty()) {
            EnhancedDeliveryLeg leg = plan.missions.get(0).legs.get(0);
            report.append("✔ Delivery assigned.\n")
                    .append("Departure: ").append(leg.departureTime).append("\n")
                    .append("Return: ").append(leg.returnTime).append("\n")
                    .append("Battery used: ").append(leg.batteryUsed).append("\n");
        } else {
            report.append("✘ No delivery scheduled\n");
        }

        return ResponseEntity.ok(report.toString());
    }

    public ResponseEntity<String> testSchedulerDebug() {

        StringBuilder dbg = new StringBuilder();
        dbg.append("=== DEBUG START ===\n\n");

        try {
            dbg.append("[4] Creating test dispatch...\n");

            MedDispatchRec rec = new MedDispatchRec();
            rec.setId(101);
            rec.setDate(LocalDate.of(2025, 1, 1));
            rec.setTime(LocalTime.parse("09:05"));

            MedDispatchRec.Requirements req = new MedDispatchRec.Requirements();
            req.setCapacity(5.0);
            req.setCooling(false);
            req.setHeating(false);
            req.setMaxCost(1000.0);
            rec.setRequirements(req);

            MedDispatchRec.Delivery del = new MedDispatchRec.Delivery();
            del.setLng(-3.191);
            del.setLat(55.943);
            rec.setDelivery(del);

            dbg.append("Dispatch created: dest=(")
                    .append(del.getLng()).append(", ").append(del.getLat()).append(")\n");

            List<MedDispatchRec> jobs = List.of(rec);

            dbg.append("[5] Running scheduler...\n");

            // PRINT BEFORE CALL
            //dbg.append("Battery before schedule=").append(battery.getCurrentCharge()).append("\n");

            EnhancedPlanResult plan = calcDeliveryPathWithScheduling(jobs);

            dbg.append("[6] Scheduler finished.\n");

            // PRINT RESULT
            if (plan.missions.isEmpty()) {
                dbg.append("✘ No missions returned!\n");
            } else {
                dbg.append("✔ Mission count: ").append(plan.missions.size()).append("\n");

                for (EnhancedDroneMission m : plan.missions) {
                    dbg.append("  Drone ").append(m.droneId).append(":\n");

                    for (EnhancedDeliveryLeg leg : m.legs) {
                        dbg.append("    Delivery=").append(leg.deliveryId).append("\n");
                        dbg.append("    Depart=").append(leg.departureTime).append("\n");
                        dbg.append("    Arrive=").append(leg.arrivalTime).append("\n");
                        dbg.append("    Return=").append(leg.returnTime).append("\n");
                        dbg.append("    BatteryUsed=").append(leg.batteryUsed).append("\n");
                        dbg.append("    BatteryAfter=").append(leg.batteryLevelAfter).append("\n");
                        dbg.append("    FlightSec=").append(leg.flightDurationSeconds).append("\n");
                    }
                }
            }

            dbg.append("\n=== DEBUG END ===\n");

            return ResponseEntity.ok(dbg.toString());

        } catch (Exception ex) {

            dbg.append("\n*** EXCEPTION OCCURRED ***\n");
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            dbg.append("\n*** EXCEPTION OCCURRED ***\n").append(sw.toString());
            return ResponseEntity.status(500).body(dbg.toString());
        }
    }



    // Utility
    private Drone cloneDrone(Drone original) {
        Drone d = new Drone();
        d.setId(original.getId());
        Capability c = new Capability();
        BatteryModel bm = new BatteryModel();
        bm.setCapacity(original.getCapability().getBattery().getCapacity());
        bm.setBaseConsumptionPerStep(original.getCapability().getBattery().getBaseConsumptionPerStep());
        bm.setConsumptionPayloadFactor(original.getCapability().getBattery().getConsumptionPayloadFactor());
        bm.setDegradationFactor(original.getCapability().getBattery().getDegradationFactor());
        bm.setCurrentCharge(original.getCapability().getBattery().getCurrentCharge());
        c.setBattery(bm);
        c.setCruiseSpeed(original.getCapability().getCruiseSpeed());
        d.setCapability(c);
        d.setServicePointId(original.getServicePointId());
        return d;
    }


    public ResponseEntity<String> testChargingScenario() {

        // Dispatch requiring ~45 Wh (will need charging)
        MedDispatchRec rec = new MedDispatchRec();
        rec.setId(101);
        rec.setDate(LocalDate.of(2025, 1, 1));
        rec.setTime(LocalTime.parse("09:05"));

        MedDispatchRec.Requirements req = new MedDispatchRec.Requirements();
        req.setCapacity(5.0);
        rec.setRequirements(req);

        MedDispatchRec.Delivery del = new MedDispatchRec.Delivery();
        del.setLng(-3.191);
        del.setLat(55.943);
        rec.setDelivery(del);

        List<MedDispatchRec> jobs = List.of(rec);

        EnhancedPlanResult plan = calcDeliveryPathWithScheduling(jobs);

        StringBuilder report = new StringBuilder();
        report.append("=== CHARGING TEST ===\n\n");

        if (!plan.missions.isEmpty()) {
            EnhancedDeliveryLeg leg = plan.missions.get(0).legs.get(0);
            report.append("✔ Delivery completed with charging!\n");
            report.append("Battery before: ").append(leg.batteryLevelBefore).append(" Wh\n");
            report.append("Recharge time: ").append(leg.rechargeTimeMinutes).append(" minutes\n");
            report.append("Battery after charge: ~").append(leg.batteryLevelBefore + (leg.rechargeTimeMinutes * 5)).append(" Wh\n");
            report.append("Battery after delivery: ").append(leg.batteryLevelAfter).append(" Wh\n");
        } else {
            report.append("✘ No delivery scheduled\n");
        }

        return ResponseEntity.ok(report.toString());
    }

    public ResponseEntity<String> testMultipleDeliveriesWithCharging() {
        // Three deliveries at different times
        List<MedDispatchRec> jobs = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            MedDispatchRec rec = new MedDispatchRec();
            rec.setId(100 + i);
            rec.setDate(LocalDate.of(2025, 1, 1));
            rec.setTime(LocalTime.parse(String.format("10:%02d", i * 15))); // 10:15, 10:30, 10:45

            MedDispatchRec.Requirements req = new MedDispatchRec.Requirements();
            req.setCapacity(5.0);
            rec.setRequirements(req);

            MedDispatchRec.Delivery del = new MedDispatchRec.Delivery();
            del.setLng(-3.191);
            del.setLat(55.943);
            rec.setDelivery(del);

            jobs.add(rec);
        }
        MedDispatchRec rec = new MedDispatchRec();
        rec.setId(999);
        rec.setDate(LocalDate.of(2025, 1, 1));
        rec.setTime(LocalTime.parse(String.format("16:59:55")));

        MedDispatchRec.Requirements req = new MedDispatchRec.Requirements();
        req.setCapacity(5.0);
        rec.setRequirements(req);

        MedDispatchRec.Delivery del = new MedDispatchRec.Delivery();
        del.setLng(-3.191);
        del.setLat(55.943);
        rec.setDelivery(del);

        jobs.add(rec);

        EnhancedPlanResult plan = calcDeliveryPathWithScheduling(jobs);

        return ResponseEntity.ok("Check console for detailed charging logs!");
    }



}
