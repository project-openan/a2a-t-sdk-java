## Task Type
Transport private line service complaint diagnosis

## Task Description
Based on <Task Object> and <Task Context>, perform network-side fault root cause diagnosis in the complaint scenario, achieve the complaint diagnosis goal defined in <Task Target>, and return the task processing result in the structure defined in <Expected Output>.

## Task Target
Diagnose network-side faults and return diagnostic result information such as fault root causes and repair suggestions.

## Task Object
{{task_object}}

## Task Context
1. Complaint category: "{{complaint_category}}"
2. Problem occurrence time: "{{problem_occurrence_time}}"
3. OSS-side event sequence number: "{{oss_event_sequence_number}}"
4. Complaint details: "{{complaint_details}}"

## Expected Output
Requirement: The complaint diagnosis task result should include the following information:
1. Diagnosis result. Allowed values: success, failure (required)
2. Diagnosis result details (required)
3. Repair suggestions (optional)
4. Fault root cause list, where each fault root cause includes fault root cause name, detailed description, repair suggestions, fault root cause point location, etc. (optional)
