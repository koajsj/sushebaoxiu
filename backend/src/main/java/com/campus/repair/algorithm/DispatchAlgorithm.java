package com.campus.repair.algorithm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Stateless and deterministic; scores use static WGS84 locations, never GPS. */
@Component
public class DispatchAlgorithm {
    public record Input(String typeName, String description, String skillType,
            Double orderLongitude, Double orderLatitude, Double workerLongitude, Double workerLatitude,
            long activeTaskCount, double rating) {}
    public record Scores(BigDecimal skillScore, BigDecimal distanceScore, BigDecimal loadScore,
            BigDecimal ratingScore, BigDecimal totalScore, Double distanceKm, String reason) {}
    private static final List<List<String>> DOMAINS = List.of(
            List.of("照明", "电路", "电工", "电气", "灯具", "插座", "开关"),
            List.of("给排水", "水管", "管道", "水暖", "水龙头", "漏水", "排水"),
            List.of("门窗", "家具", "木工", "门锁", "桌椅"),
            List.of("空调", "制冷", "风扇", "设备维修"));

    public Scores score(Input input) {
        if (input == null || input.activeTaskCount() < 0 || !Double.isFinite(input.rating())
                || input.rating() < 0 || input.rating() > 5) throw new IllegalArgumentException("Invalid score input");
        String type = input.typeName() == null ? "" : input.typeName().strip();
        String skill = input.skillType() == null ? "" : input.skillType().strip();
        // Use the category name first; the description cannot override a known category.
        var domain = DOMAINS.stream().filter(words -> containsAny(type, words)).findFirst()
                .orElseGet(() -> type.contains("其他") ? List.of() : DOMAINS.stream()
                        .filter(words -> containsAny(input.description(), words)).findFirst().orElse(List.of()));
        double skillScore;
        if (!type.isBlank() && !type.contains("其他") && skill.contains(type)) skillScore = 100;
        else if (!domain.isEmpty() && containsAny(skill, domain)) skillScore = 100;
        else if (skill.contains("综合") || skill.contains("全科")) skillScore = 70;
        else if (domain.isEmpty()) skillScore = 50;
        else skillScore = 0;
        Double distance = validCoordinate(input.orderLongitude(), input.orderLatitude())
                && validCoordinate(input.workerLongitude(), input.workerLatitude())
                ? distanceKm(input.orderLongitude(), input.orderLatitude(), input.workerLongitude(), input.workerLatitude()) : null;
        double distanceScore = distance == null ? 0 : 100 / (1 + distance);
        double loadScore = 100.0 / (1.0 + input.activeTaskCount());
        double ratingScore = input.rating() == 0 ? 50 : input.rating() * 20;
        String reason = String.format(Locale.ROOT,
                "技能%s（故障：%s；类别说明：%s；技能：%s）；%s；当前任务%d，负载分%.2f；%s；坐标：订单(%s,%s)，维修员(%s,%s)。权重40%%/30%%/20%%/10%%",
                skillScore == 100 ? "匹配" : skillScore == 70 ? "综合维修" : skillScore == 50 ? "类别不确定" : "不匹配",
                type, brief(input.description()), skill, distance == null ? "坐标缺失或无效，距离分0" : String.format(Locale.ROOT,"距离%.6f公里，距离分%.2f",distance,distanceScore),
                input.activeTaskCount(), loadScore,
                input.rating() == 0 ? "无历史评价，中性50分" : String.format(Locale.ROOT,"历史评价%.2f/5",input.rating()),
                input.orderLongitude(), input.orderLatitude(), input.workerLongitude(), input.workerLatitude());
        return new Scores(decimal(skillScore), decimal(distanceScore), decimal(loadScore), decimal(ratingScore),
                decimal(skillScore*.4 + distanceScore*.3 + loadScore*.2 + ratingScore*.1), distance, reason);
    }
    private static boolean containsAny(String value, List<String> words) {
        return value != null && words.stream().anyMatch(value::contains);
    }
    private static String brief(String value){
        if(value==null)return "";
        return value.codePointCount(0,value.length())>120?value.substring(0,value.offsetByCodePoints(0,120))+"…":value;
    }
    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP); }
    public static boolean validCoordinate(Double longitude, Double latitude) {
        return longitude != null && latitude != null && Double.isFinite(longitude) && Double.isFinite(latitude)
                && longitude >= -180 && longitude <= 180 && latitude >= -90 && latitude <= 90;
    }
    public static double distanceKm(double lon1, double lat1, double lon2, double lat2) {
        double dlat=Math.toRadians(lat2-lat1), dlon=Math.toRadians(lon2-lon1);
        double a=Math.pow(Math.sin(dlat/2),2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(dlon/2),2);
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0,a))));
    }
}
