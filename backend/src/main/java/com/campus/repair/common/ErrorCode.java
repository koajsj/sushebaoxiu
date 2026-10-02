package com.campus.repair.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    SUCCESS(0, "成功", HttpStatus.OK),
    BAD_REQUEST(40000, "请求参数不正确", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "登录状态已失效，请重新登录", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(40101, "账号或密码不正确", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "无权访问此功能", HttpStatus.FORBIDDEN),
    CONFLICT(40900, "当前状态不允许此操作，请刷新订单", HttpStatus.CONFLICT),
    PROFILE_REQUIRED(40901, "账号档案尚未完善，请联系管理员", HttpStatus.CONFLICT),
    INVALID_IMAGE(40001, "请上传有效的 PNG 或 JPEG 图片（最大5MB）", HttpStatus.BAD_REQUEST),
    INVALID_REFERENCE(40002, "所选类型、地点或图片不可用", HttpStatus.BAD_REQUEST),
    WORKER_UNAVAILABLE(40902, "维修人员当前不可用", HttpStatus.CONFLICT),
    RECORD_REQUIRED(40903, "请先填写维修记录再完成维修", HttpStatus.CONFLICT),
    RECOMMENDATION_STALE(40904, "维修人员情况已变化，请重新获取推荐", HttpStatus.CONFLICT),
    APPOINTMENT_CONFLICT(40905, "该维修人员已有时间重叠的已确认预约，请调整时间", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(40906, "本次请求标识已用于不同内容，请重新发起操作", HttpStatus.CONFLICT),
    WORKER_HAS_TASKS(40907, "该维修人员仍有在途任务，请先收回并重派", HttpStatus.CONFLICT),
    LAST_ADMIN(40908, "至少保留一位可用管理员", HttpStatus.CONFLICT),
    RATE_LIMITED(42900, "登录尝试过多，请稍后重试", HttpStatus.TOO_MANY_REQUESTS),
    UPLOAD_LIMIT(42901, "未提交图片已达上限，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    NOT_FOUND(40400, "请求的资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(40500, "请求方式不支持", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE(40600, "响应格式不支持", HttpStatus.NOT_ACCEPTABLE),
    EXPORT_TOO_LARGE(41301, "数据量超过Excel单表容量，无法生成完整报表", HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(41500, "请求内容类型不支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(50000, "服务暂时不可用，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_UNAVAILABLE(50300, "数据库暂时不可用", HttpStatus.SERVICE_UNAVAILABLE);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
