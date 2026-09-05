## Subscription Description
Please complete the network-side service recovery event subscription and reporting task based on the following <Notification Topic>, <Subscribe Condition>, <Notification Data Format>, and <Expected Output> information.

## Notification Topic
Service recovery event

## Subscribe Condition
{{subscribe_condition}} (optional)
1. Subnetwork name. Example: xx subnetwork

## Notification Data Format
{{notification_data_format}} (required)
Requirement: Please provide the following information for the content to be reported
1. Information item name (required)
2. Allowed values (optional)
3. Example (optional)

The following is an example of notification data format:
### Service Recovery Event
Requirement:
1. Service recovery plan execution status. Allowed values: not started, ended. Example: ended (required)
2. Complaint diagnosis task sequence number. Example: 9de168c0-6179-4778-8b72-4279582c0a3f (required)
3. OSS-side event sequence number. Example: event-id-202606250128 (required)
4. Access port name. Example: P781-SZ-PTN7900-23-TPA1EG24-17 (required)
5. Whether OMC automatic recovery is authorized. Allowed values: yes, no. Example: yes (required)
6. Service recovery plan name. Example: tunnel optimization (required)
7. Service recovery plan details (required)
    Example:
    Tune the list of tunnels affected by traffic bandwidth, latency violation, and packet loss violation to a new path list.
    Before optimization:
    - Tunnel 1: Tunnel a, source NE: P781-SZ-PTN7900, sink NE: P783-SZ-PTN7900, primary/secondary type: master, status: down, latency: --, packet loss rate: 100%, bandwidth: 10000bps
       - Tunnel route list:
          - Link 1: Link a, source NE: P781-SZ-PTN7900, source port: 23-TPA1EG24-17, sink NE: P782-SZ-PTN7900, sink port: 23-TPA1EG24-17, latency: --
          - Link 2: Link b, source NE: P782-SZ-PTN7900, source port: 23-TPA1EG24-17, sink NE: P783-SZ-PTN7900, sink port: 23-TPA1EG24-17, latency: --
    After optimization:
    - Tunnel 1: Tunnel b, source NE: P881-SZ-PTN7900, sink NE: P883-SZ-PTN7900, primary/secondary type: master, status: up, latency: 20ms, packet loss rate: 0.1%, bandwidth: 10000bps
       - Tunnel route list:
          - Link 1: Link c, source NE: P881-SZ-PTN7900, source port: 25-TPA1EG24-11, sink NE: P882-SZ-PTN7900, sink port: 25-TPA1EG24-12, latency: 10ms
          - Link 2: Link d, source NE: P882-SZ-PTN7900, source port: 25-TPA1EG24-13, sink NE: P883-SZ-PTN7900, sink port: 25-TPA1EG24-14, latency: 15ms
8. Service recovery plan execution end time. Example: 2026-05-11T08:21:46Z (optional)
9. Service recovery plan execution result. Allowed values: success, failure. Example: success (optional)
10. Service recovery plan execution failure reason. This information must be provided when the service recovery plan execution result is "failure". Example: After optimization, tunnel link C is occupied and tunnel optimization cannot be performed (optional)

## Expected Output
1. Subscription result. Allowed values: success
2. After successful subscription, report messages according to <Notification Data Format>
