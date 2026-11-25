package com.example.demo.model.service;

import java.time.LocalDateTime;  
import java.util.Collection;  
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;  
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.domain.Enrollment;  
import com.example.demo.model.domain.Member;  
import com.example.demo.model.domain.Post;
import com.example.demo.model.repository.EnrollmentRepository;  
import com.example.demo.model.repository.MemberRepository;
import com.example.demo.model.repository.PostRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.example.demo.model.dto.SeatMessage;
import com.example.demo.model.dto.Notification;

@Service // [11주차] 단계 4 : 신청 서비스
public class EnrollmentService {
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired // [12주차] 실시간 메시지
    private ApplicationEventPublisher eventPublisher; // [12주차] 단계 3 : 이벤트 발행 (보내기는 커밋 뒤에 RealtimeNotifier 가)

    // 내 신청 내역
    public List<Enrollment> findMine(String username) {
        return enrollmentRepository.findByMember_UsernameOrderByIdDesc(username);
    }

    // 단계4 : 바구니의모집글을한번에신청확정
    @Transactional // 단계 5 : 하나라도 실패하면 모두 취소 (롤백)
    public int confirm(String username, Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            throw new IllegalStateException("바구니가비어있습니다.");
        }
        Member member = memberRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("회원이존재하지않습니다."));
        for (Long postId : postIds) {
            Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalStateException("삭제된모집글이바구니에있습니다."));
            if (enrollmentRepository.existsByMemberAndPost(member, post)) {
                throw new IllegalStateException("이미신청한모집글입니다: " + post.getTitle());
            }
            if (post.isFull()) { // 담을때는자리가있었지만, 그사이마감됐을수있다
                throw new IllegalStateException("정원이마감되었습니다: " + post.getTitle());
            }
            Enrollment enrollment = new Enrollment();
            enrollment.setMember(member);
            enrollment.setPost(post);
            enrollment.setCreatedAt(LocalDateTime.now());
            enrollmentRepository.save(enrollment);     // ① INSERT 신청 내역
            
            post.setAppliedCount(post.getAppliedCount() + 1);
            postRepository.save(post);                 // ② UPDATE 신청 인원 + 1 (남은 자리- 1)
            eventPublisher.publishEvent(SeatMessage.from(post)); // [12주차] 단계 3 : 이벤트만 던진다 (커밋되면 RealtimeNotifier 가 보낸다)
            
            String writer = post.getAuthor().getUsername();
            if (!writer.equals(username)) { // [12주차] 단계4 : 모집글쓴사람에게알림(내글에내가신청하면생략)
                eventPublisher.publishEvent(new Notification(writer,
                    member.getName() + "님이'" + post.getTitle() + "' 에신청했습니다. ("
                        + post.getAppliedCount() + "/" + post.getCapacity() + ")", post.getId()));
            }
        }
        return postIds.size();
    }
}