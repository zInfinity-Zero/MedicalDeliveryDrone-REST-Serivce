// src/main/java/ilp/coursework/ilpcoursework1/ilp/ILPClient.java
package ilp.coursework.ilpcoursework1;

import ilp.coursework.ilpcoursework1.Drone.Drone;
import ilp.coursework.ilpcoursework1.Drone.RestrictedZone;
import ilp.coursework.ilpcoursework1.Drone.ServicePoint;
import ilp.coursework.ilpcoursework1.Util.DeliveryPathDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class ILPService {

    private final WebClient webClient;

    public ILPService(WebClient webClient) {
        this.webClient = webClient;
    }

    public List<ServicePoint> fetchServicePoints() {
        ServicePoint[] arr = webClient.get()
                .uri("/service-points")
                .retrieve()
                .bodyToMono(ServicePoint[].class)
                .block();
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    public List<RestrictedZone> fetchRestrictedAreas() {
        RestrictedZone[] arr = webClient.get()
                .uri("/restricted-areas")
                .retrieve()
                .bodyToMono(RestrictedZone[].class)
                .block();
        return arr == null ? List.of() : Arrays.asList(arr);
    }

    public List<Drone> fetchDrones() {
        Drone[] arr = webClient.get()
                .uri("/drones")
                .retrieve()
                .bodyToMono(Drone[].class)
                .block();
        return arr == null ? List.of() : Arrays.asList(arr);
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
