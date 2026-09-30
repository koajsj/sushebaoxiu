package com.campus.repair.vo;

import com.campus.repair.entity.RepairOrderEntity;

public record OrderVO(Long id, Long studentId, Long typeId, String title, String description,
        String imageUrl, Long buildingId, String roomNo, String priority, String status,
        Long workerId, java.time.LocalDateTime createTime, java.time.LocalDateTime updateTime,
        String typeName, String buildingName, String workerName, String studentName,
        Integer repairRound, Integer dispatchRound, java.time.LocalDateTime assignedTime,
        java.time.LocalDateTime acceptedTime, java.time.LocalDateTime startedTime,
        java.time.LocalDateTime responseDueTime, java.time.LocalDateTime repairDueTime, String overdueType,
        java.time.LocalDateTime appointmentStart, java.time.LocalDateTime appointmentEnd,
        String appointmentStatus, String appointmentReason, Integer appointmentVersion) {
    public static OrderVO from(RepairOrderEntity order, String type, String building, String worker, String student) {
        return new OrderVO(order.getId(), order.getStudentId(), order.getTypeId(), order.getTitle(),
                order.getDescription(), order.getImageUrl(), order.getBuildingId(), order.getRoomNo(),
                order.getPriority(), order.getStatus(), order.getWorkerId(), order.getCreateTime(),
                order.getUpdateTime(), type, building, worker, student, order.getRepairRound(), order.getDispatchRound(),
                order.getAssignedTime(), order.getAcceptedTime(), order.getStartedTime(),order.getResponseDueTime(),
                order.getRepairDueTime(),order.getOverdueType(),order.getAppointmentStart(),order.getAppointmentEnd(),
                order.getAppointmentStatus(),order.getAppointmentReason(),order.getAppointmentVersion());
    }
}
