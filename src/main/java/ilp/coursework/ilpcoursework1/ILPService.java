// src/main/java/ilp/coursework/ilpcoursework1/ilp/ILPClient.java
package ilp.coursework.ilpcoursework1;

import ilp.coursework.ilpcoursework1.CW3.BatteryModel;
import ilp.coursework.ilpcoursework1.Drone.*;
import ilp.coursework.ilpcoursework1.Util.DeliveryPathDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class ILPService {

    private final WebClient webClient;

    public ILPService(WebClient webClient) {
        this.webClient = webClient;
    }

    public List<ServicePoint> fetchServicePointsCW2() {
        ServicePoint[] arr = webClient.get()
                .uri("/service-points")
                .retrieve()
                .bodyToMono(ServicePoint[].class)
                .block();
        return arr == null ? List.of() : Arrays.asList(arr);
    }
    public List<ServicePoint> fetchServicePoints() {

        ServicePoint[] arr = webClient.get()
                .uri("/service-points")
                .retrieve()
                .bodyToMono(ServicePoint[].class)
                .block();

        if (arr == null) return List.of();

        List<ServicePoint> sps = Arrays.asList(arr);

        // Inject missing scheduling/recharge settings
        for (ServicePoint sp : sps) {

            // === Slot limit (for queueing) ===
            if (sp.getMaxConcurrentSlots() <= 0) {
                sp.setMaxConcurrentSlots(2);  // default 2 drones at a time
            }

            // === Recharge rate (Wh per minute) ===
            if (sp.getRechargeRate() <= 0) {
                sp.setRechargeRate(5.0);     // default charging speed
            }

            // === Loading/unloading removed, but set 0 to be safe ===
            if (sp.getLoadingTime() <= 0) sp.setLoadingTime(0);
            if (sp.getUnloadingTime() <= 0) sp.setUnloadingTime(0);

            // === Full recharge time only used if > 0 ===
            if (sp.getFullRechargeTime() <= 0) {
                sp.setFullRechargeTime(0);
            }
        }

        return sps;
    }



    public List<RestrictedZone> fetchRestrictedAreas() {
        RestrictedZone[] arr = webClient.get()
                .uri("/restricted-areas")
                .retrieve()
                .bodyToMono(RestrictedZone[].class)
                .block();
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    public List<Drone> fetchDronesCW2() {
        Drone[] arr = webClient.get()
                .uri("/drones")
                .retrieve()
                .bodyToMono(Drone[].class)
                .block();
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    public List<Drone> fetchDrones() {

        Drone[] arr = webClient.get()
                .uri("/drones")
                .retrieve()
                .bodyToMono(Drone[].class)
                .block();

        List<Drone> list = arr == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(arr));

        for (Drone d : list) {
            Capability cap = d.getCapability();
            if (cap == null) continue;

            // === Battery model defaults ===
            if (cap.getBattery() == null) {
                BatteryModel bm = new BatteryModel();
                bm.setCapacity(100.0);                  // default capacity
                bm.setBaseConsumptionPerStep(0.5);      // base drain
                bm.setConsumptionPayloadFactor(0.2);    // additional per payload
                bm.setDegradationFactor(0.1);
                bm.setCurrentCharge(30.0); //low charge
                cap.setBattery(bm);
            }

            // === Cruise speed default ===
            if (cap.getCruiseSpeed() <= 0) {
                cap.setCruiseSpeed(0.00015);               //lnglat speed
            }
            cap.setCapacity(0);//disabling ilpdroes
        }


        return list;
    }




    ///DTO for service point drone
 /// this has no reason to be here but not putting here resolves in some sort of timeout issue

public static class DroneAvailabilityDTO {
    private String dayOfWeek;
    private String from;
    private String until;


    public String getDayOfWeek() {
        return dayOfWeek;
    }
    public void setDayOfWeek(String dayOfWeek) {this.dayOfWeek = dayOfWeek;}

    public String getFrom() {
        return from;
    }
    public void setFrom(String from) {this.from = from;}
    public String getUntil() {return until; }
    public void setUntil(String until) {this.until = until;}

}
    public static class ServicePointDroneDTO {
        private String id;
        public String getId() {return id;}
        public void setId(String id) {this.id = id;}
        private List<DroneAvailabilityDTO> availability;

        public List<DroneAvailabilityDTO> getAvailability() {return availability;}
        public void setAvailability(List<DroneAvailabilityDTO> availability) {this.availability = availability;}
    }
    public static class ServicePointDroneListDTO {
        private int servicePointId;
        private List<ServicePointDroneDTO> drones;

        public int getServicePointId() {return servicePointId;}
        public void setServicePointId(int servicePointId) {this.servicePointId = servicePointId;}

        public List<ServicePointDroneDTO> getDrones() {
            return drones;
        }
        public void setDrones(List<ServicePointDroneDTO> drones) {this.drones = drones;}
    }


    public List<ServicePointDroneListDTO> fetchDronesForServicePoints() {
        ServicePointDroneListDTO[] arr = webClient.get()
                .uri("/drones-for-service-points")
                .retrieve()
                .bodyToMono(ServicePointDroneListDTO[].class)
                .block();

        return arr == null ? List.of() : Arrays.asList(arr);
    }



}
