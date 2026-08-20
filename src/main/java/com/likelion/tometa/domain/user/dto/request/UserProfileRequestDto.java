package com.likelion.tometa.domain.user.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class UserProfileRequestDto {

    @Size(min = 1, max = 10, message = "닉네임은 1자 이상 10자 이하여야 합니다.")
    @Pattern(
            regexp = "^[가-힣a-zA-Z0-9]+$",
            message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."
    )
    private String nickname;

    @Pattern(
            regexp = "^(male|female)$",
            message = "올바른 성별을 선택해주세요."
    )
    private String gender;

    @Pattern(
            regexp = "^(10s|20s|30s|40s|etc)$",
            message = "올바른 나이대를 선택해주세요."
    )
    private String ageGroup;

    @Pattern(
            regexp = "^(dry|oily|combination_dry|combination|sensitive|unknown)$",
            message = "올바른 피부 타입을 선택해주세요."
    )
    private String skinType;

    @AssertTrue(message = "수정할 프로필 정보를 입력해주세요.")
    private boolean anyFieldProvided;

    @AssertTrue(message = "닉네임은 null일 수 없습니다.")
    private boolean nicknameNonNull = true;

    @AssertTrue(message = "성별은 null일 수 없습니다.")
    private boolean genderNonNull = true;

    @AssertTrue(message = "나이대는 null일 수 없습니다.")
    private boolean ageGroupNonNull = true;

    @AssertTrue(message = "피부 타입은 null일 수 없습니다.")
    private boolean skinTypeNonNull = true;

    private boolean nicknameProvided;
    private boolean genderProvided;
    private boolean ageGroupProvided;
    private boolean skinTypeProvided;

    public UserProfileRequestDto() {
    }

    public String nickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameProvided = true;
        this.nicknameNonNull = nickname != null;
        this.anyFieldProvided = true;
    }

    public String gender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
        this.genderProvided = true;
        this.genderNonNull = gender != null;
        this.anyFieldProvided = true;
    }

    public String ageGroup() {
        return ageGroup;
    }

    public void setAgeGroup(String ageGroup) {
        this.ageGroup = ageGroup;
        this.ageGroupProvided = true;
        this.ageGroupNonNull = ageGroup != null;
        this.anyFieldProvided = true;
    }

    public String skinType() {
        return skinType;
    }

    public void setSkinType(String skinType) {
        this.skinType = skinType;
        this.skinTypeProvided = true;
        this.skinTypeNonNull = skinType != null;
        this.anyFieldProvided = true;
    }

    public boolean hasNickname() {
        return nicknameProvided;
    }

    public boolean hasGender() {
        return genderProvided;
    }

    public boolean hasAgeGroup() {
        return ageGroupProvided;
    }

    public boolean hasSkinType() {
        return skinTypeProvided;
    }
}
