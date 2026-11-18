package dev.guardflow.server.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AlgorithmController {

    private static final List<AlgorithmInfo> ALGORITHMS = List.of(
            new AlgorithmInfo("token_bucket", "令牌桶", "允许突发，空闲后最多一次性放行 capacity 个", List.of(
                    new ParamInfo("capacity", "桶容量", "int", 1, 1000, 1, 10, null),
                    new ParamInfo("ratePerSecond", "补充速率", "double", 0.1, 1000, 0.1, 2, "个/秒"))),
            new AlgorithmInfo("fixed_window", "固定窗口", "窗口边界处可能出现 2 倍突发", List.of(
                    new ParamInfo("limit", "窗口配额", "int", 1, 1000, 1, 10, null),
                    new ParamInfo("windowMillis", "窗口长度", "int", 10, 60000, 100, 1000, "ms"))),
            new AlgorithmInfo("sliding_window", "滑动窗口", "任意 1 秒内严格不超过配额，无边界突发", List.of(
                    new ParamInfo("limit", "窗口配额", "int", 1, 1000, 1, 10, null),
                    new ParamInfo("windowMillis", "窗口跨度", "int", 10, 60000, 100, 1000, "ms"))),
            new AlgorithmInfo("leaky_bucket", "漏桶", "恒定速率流出，突发只能排队或被拒", List.of(
                    new ParamInfo("capacity", "队列容量", "int", 1, 1000, 1, 10, null),
                    new ParamInfo("ratePerSecond", "流出速率", "double", 0.1, 1000, 0.1, 2, "个/秒")))
    );

    private final Environment environment;

    public AlgorithmController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/algorithms")
    public List<AlgorithmInfo> algorithms() {
        return ALGORITHMS;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length == 0 ? "default" : String.join(",", profiles);
        return Map.of("status", "UP", "profile", profile);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AlgorithmInfo(String id, String displayName, String summary, List<ParamInfo> params) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParamInfo(String key, String label, String type, Number min, Number max, Number step,
                            @JsonProperty("default") Number defaultValue, String unit) {
    }
}
