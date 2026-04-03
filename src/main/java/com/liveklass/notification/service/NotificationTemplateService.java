package com.liveklass.notification.service;

import com.liveklass.notification.api.dto.NotificationTemplateRequest;
import com.liveklass.notification.api.dto.NotificationTemplateResponse;
import com.liveklass.notification.common.exception.CustomException;
import com.liveklass.notification.common.exception.ErrorCode;
import com.liveklass.notification.domain.template.NotificationTemplate;
import com.liveklass.notification.domain.template.NotificationTemplateRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationTemplateService {

    private final NotificationTemplateRepository templateRepository;

    @Transactional
    public NotificationTemplateResponse create(NotificationTemplateRequest request) {
        if (templateRepository.existsByTypeAndChannel(request.type(), request.channel())) {
            throw new CustomException(ErrorCode.TEMPLATE_ALREADY_EXISTS);
        }

        NotificationTemplate template = NotificationTemplate.builder()
            .type(request.type())
            .channel(request.channel())
            .titleTemplate(request.titleTemplate())
            .contentTemplate(request.contentTemplate())
            .build();

        templateRepository.save(template);
        return NotificationTemplateResponse.from(template);
    }

    public List<NotificationTemplateResponse> findAll() {
        return templateRepository.findAllByOrderByTypeAscChannelAsc()
            .stream()
            .map(NotificationTemplateResponse::from)
            .toList();
    }

    public NotificationTemplateResponse findById(Long templateId) {
        NotificationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));
        return NotificationTemplateResponse.from(template);
    }

    @Transactional
    public NotificationTemplateResponse update(Long templateId, NotificationTemplateRequest request) {
        NotificationTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new CustomException(ErrorCode.TEMPLATE_NOT_FOUND));
        template.update(request.titleTemplate(), request.contentTemplate());
        return NotificationTemplateResponse.from(template);
    }

    @Transactional
    public void delete(Long templateId) {
        if (!templateRepository.existsById(templateId)) {
            throw new CustomException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        templateRepository.deleteById(templateId);
    }
}
