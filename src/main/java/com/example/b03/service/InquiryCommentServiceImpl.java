package com.example.b03.service;

import com.example.b03.domain.Inquiry;
import com.example.b03.domain.InquiryComment;
import com.example.b03.domain.Member;
import com.example.b03.dto.InquiryCommentRequestDTO;
import com.example.b03.dto.InquiryCommentResponseDTO;
import com.example.b03.repository.InquiryCommentRepository;
import com.example.b03.repository.InquiryRepository;
import com.example.b03.repository.MemberRepository;
import com.example.b03.repository.MembershipTypeRepository; // MembershipTypeRepository 임포트 여부 확인
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor
public class InquiryCommentServiceImpl implements InquiryCommentService {

    private final InquiryCommentRepository inquiryCommentRepository;
    private final InquiryRepository inquiryRepository;
    private final MemberRepository memberRepository;
    private final MembershipTypeRepository membershipTypeRepository;
    private final ModelMapper modelMapper;

    // 관리자 멤버십 타입 ID (상수로 정의하거나 DB에서 조회)
    private static final Byte ADMIN_MEMBERSHIP_TYPE_ID = 1; // ⭐ 관리자 멤버십 타입 ID를 1로 가정

    @Override
    public InquiryCommentResponseDTO createComment(InquiryCommentRequestDTO requestDTO) {
        Inquiry inquiry = inquiryRepository.findById(requestDTO.getInquiryId())
                .orElseThrow(() -> new IllegalArgumentException("해당 문의를 찾을 수 없습니다: " + requestDTO.getInquiryId()));

        if (inquiry.getIsDeleted()) {
            throw new IllegalArgumentException("삭제된 문의에는 답변을 작성할 수 없습니다.");
        }

        Member admin = memberRepository.findById(requestDTO.getAdminNo())
                .orElseThrow(() -> new IllegalArgumentException("해당 관리자를 찾을 수 없습니다: " + requestDTO.getAdminNo()));

        // ⭐⭐⭐ 최종 수정: getTypeId()로 변경! ⭐⭐⭐
        if (admin.getMembershipType() == null || !admin.getMembershipType().getTypeId().equals(ADMIN_MEMBERSHIP_TYPE_ID)) {
            throw new IllegalArgumentException("관리자만 답변을 작성할 수 있습니다.");
        }

        InquiryComment comment = InquiryComment.builder()
                .inquiry(inquiry)
                .admin(admin)
                .content(requestDTO.getContent())
                .build();
        inquiryCommentRepository.save(comment);

        log.info("새로운 답변 등록: " + comment.getCommentId());
        return entityToDto(comment);
    }

    @Override
    public InquiryCommentResponseDTO updateComment(Integer commentId, InquiryCommentRequestDTO requestDTO) {
        InquiryComment comment = inquiryCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 답변을 찾을 수 없습니다: " + commentId));

        if (comment.getIsDeleted()) {
            throw new IllegalArgumentException("삭제된 답변은 수정할 수 없습니다.");
        }

        if (!comment.getAdmin().getMemberNo().equals(requestDTO.getAdminNo())) {
            throw new IllegalArgumentException("답변 작성 관리자만 수정할 수 있습니다.");
        }

        comment.setContent(requestDTO.getContent());
        // inquiryCommentRepository.save(comment);

        log.info("답변 수정 완료: " + comment.getCommentId());
        return entityToDto(comment);
    }

    @Override
    public void deleteComment(Integer commentId, Integer adminNo) {
        InquiryComment comment = inquiryCommentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 답변을 찾을 수 없습니다: " + commentId));

        if (comment.getIsDeleted()) {
            throw new IllegalArgumentException("이미 삭제된 답변입니다.");
        }

        if (!comment.getAdmin().getMemberNo().equals(adminNo)) {
            throw new IllegalArgumentException("답변 작성 관리자만 삭제할 수 있습니다.");
        }

        comment.setIsDeleted(true);
        inquiryCommentRepository.save(comment);

        log.info("답변 삭제 완료 (논리적 삭제): " + commentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InquiryCommentResponseDTO> getCommentsForInquiry(Integer inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new IllegalArgumentException("해당 문의를 찾을 수 없습니다: " + inquiryId));

        List<InquiryComment> comments = inquiryCommentRepository.findByInquiryAndIsDeletedFalseOrderByCreatedAtAsc(inquiry);

        return comments.stream()
                .map(this::entityToDto)
                .collect(Collectors.toList());
    }

    private InquiryCommentResponseDTO entityToDto(InquiryComment inquiryComment) {
        InquiryCommentResponseDTO dto = modelMapper.map(inquiryComment, InquiryCommentResponseDTO.class);

        if (inquiryComment.getInquiry() != null) {
            dto.setInquiryId(inquiryComment.getInquiry().getInquiryId());
        }
        if (inquiryComment.getAdmin() != null) {
            dto.setAdminNo(inquiryComment.getAdmin().getMemberNo());
        }

        return dto;
    }
}