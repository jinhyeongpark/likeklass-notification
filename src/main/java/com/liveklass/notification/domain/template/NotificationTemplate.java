package com.liveklass.notification.domain.template;

import com.liveklass.notification.domain.notification.NotificationChannel;
import com.liveklass.notification.domain.notification.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_templates", uniqueConstraints = {
    @UniqueConstraint(name = "uk_template_type_channel", columnNames = {"type", "channel"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(nullable = false, length = 200)
    private String titleTemplate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contentTemplate;

    public void update(String titleTemplate, String contentTemplate) {
        this.titleTemplate = titleTemplate;
        this.contentTemplate = contentTemplate;
    }

    /**
     * 템플릿 문자열에서 {key} 플레이스홀더를 referenceData의 값으로 치환합니다.
     * 예: "{amount}원 결제가 완료되었습니다." + {amount: 39000} → "39000원 결제가 완료되었습니다."
     */
    public String resolveTitle(Map<String, Object> referenceData) {
        return resolve(titleTemplate, referenceData);
    }

    public String resolveContent(Map<String, Object> referenceData) {
        return resolve(contentTemplate, referenceData);
    }

    private String resolve(String template, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = data.getOrDefault(key, "{" + key + "}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
