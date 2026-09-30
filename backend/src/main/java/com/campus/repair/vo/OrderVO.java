package com.campus.repair.vo;

import com.campus.repair.entity.RepairOrderEntity;

public record OrderVO(Long id, Long studentId, Long typeId, String title, String description,
        String imageUrl, Long buildingId, String roomNo, String priority, String status,
        Long workerId, java.time.LocalDateTime createTime, java.time.LocalDateTime updateTime,
        String typeName, String buildingName, String workerName, String studentName) {
    public static OrderVO from(RepairOrderEntity order, String type, String building, String worker, String student) {
        return new OrderVO(order.getId(), order.getStudentId(), order.getTypeId(), order.getTitle(),
                order.getDescription(), order.getImageUrl(), order.getBuildingId(), order.getRoomNo(),
                order.getPriority(), order.getStatus(), order.getWorkerId(), order.getCreateTime(),
                order.getUpdateTime(), type, building, worker, student);
    }
}
