package com.campus.repair.algorithm;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DispatchAlgorithmTest {
    private final DispatchAlgorithm algorithm = new DispatchAlgorithm();
    private DispatchAlgorithm.Input input(String skill, Double longitude, Double latitude, long load, double rating) {
        return new DispatchAlgorithm.Input("照明与电路", "灯具开关", skill, 116.3, 39.9, longitude, latitude, load, rating);
    }
    @Test void matchingNearLightWorkerOutranksGenericFarWorker() {
        var a=algorithm.score(input("电工",116.3,39.9,0,4.8));
        var b=algorithm.score(input("综合维修",116.4,40.0,4,4.8));
        assertTrue(a.totalScore().compareTo(b.totalScore())>0);
        assertEquals(new BigDecimal("99.60"),a.totalScore());
    }
    @Test void weightsUseUnroundedFactors() {
        var scores=algorithm.score(input("电工",116.3,39.9,1,4));
        assertEquals(new BigDecimal("88.00"),scores.totalScore());
        assertEquals(new BigDecimal("50.00"),scores.loadScore());
    }
    @Test void missingCoordinateIsExplainedAndReceivesZero() {
        var scores=algorithm.score(input("电工",null,null,0,0));
        assertEquals(new BigDecimal("0.00"),scores.distanceScore());
        assertNull(scores.distanceKm());
        assertTrue(scores.reason().contains("坐标缺失"));
        assertTrue(scores.reason().contains("无历史评价"));
        assertEquals(new BigDecimal("50.00"),scores.ratingScore());
    }
    @Test void coordinateZeroIsValidButOutOfRangeIsNot() {
        assertTrue(DispatchAlgorithm.validCoordinate(0.0,0.0));
        assertFalse(DispatchAlgorithm.validCoordinate(181.0,0.0));
        assertFalse(DispatchAlgorithm.validCoordinate(0.0,91.0));
        assertFalse(DispatchAlgorithm.validCoordinate(Double.NaN,0.0));
        assertFalse(DispatchAlgorithm.validCoordinate(null,0.0));
    }
    @Test void haversineSupportsDatelineAndKnownDistance() {
        assertEquals(111.195, DispatchAlgorithm.distanceKm(0,0,1,0), .01);
        assertEquals(222.39, DispatchAlgorithm.distanceKm(179,0,-179,0), .02);
    }
    @Test void skillCategoriesDoNotShareEquipmentGenericWord() {
        assertEquals(new BigDecimal("100.00"),algorithm.score(input("照明与电路",116.3,39.9,0,5)).skillScore());
        assertEquals(new BigDecimal("0.00"),algorithm.score(input("给排水",116.3,39.9,0,5)).skillScore());
        var air=new DispatchAlgorithm.Input("空调与设备","公共设备", "照明与电路",116.3,39.9,116.3,39.9,0,5);
        assertEquals(new BigDecimal("0.00"),algorithm.score(air).skillScore());
    }
    @Test void genericAndOtherSkillsHaveDocumentedScores() {
        assertEquals(new BigDecimal("70.00"),algorithm.score(input("校园综合维修",116.3,39.9,0,5)).skillScore());
        var other=new DispatchAlgorithm.Input("其他问题","不确定故障","木工",null,null,null,null,0,0);
        assertEquals(new BigDecimal("50.00"),algorithm.score(other).skillScore());
    }
    @Test void moreTasksAndGreaterDistanceReduceScores() {
        var base=algorithm.score(input("电工",116.3,39.9,0,5));
        var busy=algorithm.score(input("电工",116.31,39.91,10,5));
        assertTrue(base.loadScore().compareTo(busy.loadScore())>0);
        assertTrue(base.distanceScore().compareTo(busy.distanceScore())>0);
    }
    @Test void invalidInputsAreRejectedRatherThanProducingNan() {
        assertThrows(IllegalArgumentException.class,()->algorithm.score(input("电工",116.3,39.9,-1,5)));
        assertThrows(IllegalArgumentException.class,()->algorithm.score(input("电工",116.3,39.9,0,6)));
        assertThrows(IllegalArgumentException.class,()->algorithm.score(input("电工",116.3,39.9,0,Double.NaN)));
    }
    @Test void reasonPreservesInputsForStaleSnapshotCheck() {
        var before=algorithm.score(input("电工",116.3,39.9,0,5));
        var moved=algorithm.score(input("电工",116.3000001,39.9,0,5));
        assertNotEquals(before.reason(),moved.reason());
        assertTrue(before.reason().contains("当前任务0"));
    }
    @Test void typeDescriptionIsPartOfSnapshotEvenWhenScoreUnchanged() {
        var a=new DispatchAlgorithm.Input("照明与电路","灯具故障","电工",116.3,39.9,116.3,39.9,0,5);
        var b=new DispatchAlgorithm.Input("照明与电路","灯具及开关故障","电工",116.3,39.9,116.3,39.9,0,5);
        assertEquals(algorithm.score(a).totalScore(),algorithm.score(b).totalScore());
        assertNotEquals(algorithm.score(a).reason(),algorithm.score(b).reason());
    }
}
