package com.sparta.post03.service;


import com.sparta.post03.dto.request.LoginRequestDto;
import com.sparta.post03.dto.response.ResponseDto;
import com.sparta.post03.dto.TokenDto;
import com.sparta.post03.dto.request.MemberRequestDto;
import com.sparta.post03.dto.response.MemberResponseDto;
import com.sparta.post03.entity.RefreshToken;
import com.sparta.post03.entity.Member;
import com.sparta.post03.exception.DuplicateMemberException;
import com.sparta.post03.exception.ErrorCode;
import com.sparta.post03.exception.ErrorResponse;
import com.sparta.post03.exception.passwordConfirmException;
import com.sparta.post03.jwt.provider.JwtProvider;
import com.sparta.post03.repository.RefreshTokenRopository;
import com.sparta.post03.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    private final RefreshTokenRopository refreshTokenRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final JwtProvider jwtProvider;

    //가입한 회원인지 아닌지 유효성 검사해주는 method
    public Member isPresentMember(String username){
        Optional<Member> optionalMember = memberRepository.findByUsername(username);
        return optionalMember.orElse(null);
    }
    //회원가입
    @Transactional
    public ResponseEntity<?> registerUser(MemberRequestDto memberRequestDto){  //제네릭이 ?인 이유는 성공인지 실패인지 모르니까

        if(null != isPresentMember(memberRequestDto.getUsername())){            //여기는 실패시
            throw new DuplicateMemberException();
        }
        if(!memberRequestDto.getPassword().equals(memberRequestDto.getPasswordConfirm())){
//            return new ResponseEntity<>(ErrorCode.BAD_PASSWORD_CONFIRM.getMessage())
            throw new passwordConfirmException();
        }

        Member member = Member.builder()                                        //여기는 성공 시
                .username(memberRequestDto.getUsername())
                .password(passwordEncoder.encode(memberRequestDto.getPassword()))
                .build();
        memberRepository.save(member);
        return ResponseEntity.ok().body(ResponseDto.success(
                MemberResponseDto.builder()
                        .id(member.getId())
                        .username(member.getUsername())
                        .createdAt(member.getCreatedAt())
                        .modifiedAt(member.getModifiedAt())
                        .build())
        );
    }

    //로그인
    public ResponseEntity<?> login(LoginRequestDto loginRequestDto, HttpServletResponse httpServletResponse) {
        Member member = isPresentMember(loginRequestDto.getUsername());
        if(null == member){
            return ErrorResponse.fail("MEMBER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        if(!member.validatePassword(passwordEncoder, loginRequestDto.getPassword())){
            return ErrorResponse.fail("INVALID_MEMBER", "사용자를 찾을수 없습니다.");
        }


        // 1. Login ID/PW 를 기반으로 AuthenticationToken 생성
        UsernamePasswordAuthenticationToken authenticationToken
                = new UsernamePasswordAuthenticationToken(loginRequestDto.getUsername(), loginRequestDto.getPassword());

        // 2. 실제로 검증 (사용자 비밀번호 체크) 이 이루어지는 부분
        //    authenticate 메서드가 실행이 될 때 CustomUserDetailsService 에서 만들었던 loadUserByUsername 메서드가 실행됨
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        TokenDto tokenDto = jwtProvider.generateTokenDto(authentication);

        RefreshToken refreshToken = RefreshToken.builder()
                .key(authentication.getName())
                .value(tokenDto.getRefreshToken())
                .build();

        refreshTokenRepository.save(refreshToken);

        httpServletResponse.addHeader("Access-Token", tokenDto.getGrantType() + " " + tokenDto.getAccessToken());
        httpServletResponse.addHeader("Refresh-Token", tokenDto.getRefreshToken());

        return ResponseEntity.ok().body(ResponseDto.success(
                MemberResponseDto.builder()
                        .id(member.getId())
                        .username(member.getUsername())
                        .createdAt(member.getCreatedAt())
                        .modifiedAt(member.getModifiedAt())
                        .build())
        );
    }
}