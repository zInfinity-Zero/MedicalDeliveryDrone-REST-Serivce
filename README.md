# Medical-Delivery Drone Scheduler

This service computes feasible drone delivery schedules given:
- Drone metadata (capabilities, battery models, cruise speeds)
- Service-point metadata (location, recharge rates, queueing constraints)
- Restricted flight zones
- Time-stamped medical delivery requests

The system uses an availability-aware scheduler combined with a battery-aware
A* pathfinding component.

## Core Concepts

### 1. A* Pathfinding With Battery Tracking
Each movement step:
- Consumes energy based on baseConsumptionPerStep and payloadFraction.
- Applies a degradation multiplier reflecting load-dependent inefficiencies.
- Advances time based on cruiseSpeed (stepDistance / speed).

The A* search returns:
- List of coordinates representing the flight path.
- Battery used.
- Time elapsed.

### 2. Scheduling Model
Each drone maintains:
- Current time (real operating timeline).
- Current battery level.
- Availability windows for each weekday.

A drone may be in one of three charging modes:
- Passive charging while idle before dispatch time.
- Active charging when appointed for delivery but insufficient power remains.
- No charging while not available or in flight.

### 3. Assignment Strategy
For every scheduling round:
1. Evaluate each drone and each unassigned delivery.
2. Compute passive charging up to the delivery’s dispatch time.
3. Estimate outbound and return consumption through A*.
4. Require consumption ≤ drone battery capacity.
5. Estimate queue delay and check availability windows.
6. Produce a set of feasible (drone, delivery) options.
7. Select the earliest-ready option based on totalReadyTime.
8. Execute outbound and return flights.
9. Update battery and time for the drone.

This repeats until all deliveries are either assigned or infeasible.

### 4. Impossible Deliveries
Deliveries requiring more energy than a drone’s maximum battery capacity
are rejected and remain unassigned.

### 5. Service-Point Constraints
- One departure/arrival slot per service point.
- Unlimited simultaneous recharging sessions.
- Recharge rate provided per service point.

### 6. Output Format
The scheduler returns:
- A set of missions, one per drone.
- Each mission contains ordered delivery legs.
- Each leg reports:
    - departure and arrival times
    - battery levels before and after flight
    - recharge time
    - total flight duration
    - full coordinate path

