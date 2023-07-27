package com.prwatech.coupon.service.impl;

import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.coupon.dto.AddCouponDto;
import com.prwatech.coupon.dto.GetCouponDto;
import com.prwatech.coupon.model.Coupon;
import com.prwatech.coupon.model.UsersCoupon;
import com.prwatech.coupon.repository.CouponRepository;
import com.prwatech.coupon.repository.UserCouponRepository;
import com.prwatech.coupon.repository.UserCouponTemplate;
import com.prwatech.coupon.service.CouponService;
import com.prwatech.user.model.User;
import com.prwatech.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

@Component
@Transactional
@AllArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserCouponTemplate userCouponTemplate;
    private final UserRepository userRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(CouponServiceImpl.class);

    @Override
    public List<Coupon> addNewCoupons(List<AddCouponDto> addCouponDtoList) {

        List<Coupon> coupons = new ArrayList<>();
      for(AddCouponDto addCouponDto: addCouponDtoList){
          if(addCouponDto.getCode()==null || addCouponDto.getAmountOff()==null){
              LOGGER.error("Add new coupon :: Coupon code is null!");
          }
          else{
              coupons.add(Coupon.builder()
                      .code(addCouponDto.getCode())
                      .amountOff(addCouponDto.getAmountOff())
                      .activeDuration(LocalDateTime.now().plusMonths(3l))
                      .isActive(Boolean.TRUE)
                      .build());
          }
      }

      if(!coupons.isEmpty()){
          return couponRepository.saveAll(coupons);
      }
      return null;
    }

    @Override
    public List<GetCouponDto> getAllCouponByUserId(ObjectId userId) {

        if(userId==null){
            LOGGER.info("User id is null in coupon get list!");
            throw new UnProcessableEntityException("UserId is null!");
        }

        List<UsersCoupon> usersCoupons = userCouponTemplate.getByUserId(userId);
        List<GetCouponDto> getCouponDtoList = new ArrayList<>();

        for (UsersCoupon usersCoupon:usersCoupons) {
            Optional<Coupon> couponOptional =couponRepository.findById(usersCoupon.getCouponId().toString());
            if(couponOptional.isPresent()){
                getCouponDtoList.add(GetCouponDto.builder()
                        .couponId(usersCoupon.getId())
                        .userId(usersCoupon.getUserId().toString())
                        .code(couponOptional.get().getCode())
                        .amountOff(couponOptional.get().getAmountOff())
                        .isScratched(usersCoupon.getIsScratched())
                        .isRedeemed(usersCoupon.getIsRedeemed())
                        .isActive(couponOptional.get().getIsActive())
                        .validTillDate(couponOptional.get().getActiveDuration())
                        .build());
            }
        }
        return getCouponDtoList;
    }

    @Override
    public GetCouponDto scratchCoupon(ObjectId userId, ObjectId couponId) {
        if(userId==null || couponId==null){
            LOGGER.error("Scratch coupon :: user id or users-coupon is null");
        }
        UsersCoupon usersCoupon = userCouponTemplate.getByUserIdAndCouponId(userId, couponId);
        if(Objects.nonNull(usersCoupon)) {
            if (!usersCoupon.getIsScratched()) {
                usersCoupon.setIsScratched(Boolean.TRUE);
                userCouponRepository.save(usersCoupon);
            }

            Optional<Coupon> couponOptional = couponRepository.findById(usersCoupon.getCouponId().toString());
            if (couponOptional.isPresent()) {
                return GetCouponDto.builder()
                        .couponId(usersCoupon.getId())
                        .userId(usersCoupon.getUserId().toString())
                        .code(couponOptional.get().getCode())
                        .amountOff(couponOptional.get().getAmountOff())
                        .isScratched(usersCoupon.getIsScratched())
                        .isRedeemed(usersCoupon.getIsRedeemed())
                        .isActive(couponOptional.get().getIsActive())
                        .validTillDate(couponOptional.get().getActiveDuration())
                        .build();
            }
            else {
                LOGGER.error("Scratched coupon :: No coupon find by this coupon id :: {}", couponId);
            }
        }

        return null;
    }

    @Override
    public GetCouponDto redeemCoupon(ObjectId userId,  String code) {
        if (userId == null || code == null) {
            LOGGER.error("Scratch coupon :: user id or users-coupon is null");
        }

        Optional<Coupon> couponOptional = couponRepository.findByCode(code);
        if(!couponOptional.isPresent()){
            throw new UnProcessableEntityException("Invalid coupon code.");
        }
        UsersCoupon usersCoupon = userCouponTemplate.getByUserIdAndCouponId(userId, new ObjectId(couponOptional.get().getId()));
        if (Objects.nonNull(usersCoupon)) {
            if (usersCoupon.getIsRedeemed()) {
                throw new UnProcessableEntityException("This coupon is already used!");
            }

            Coupon coupon = couponOptional.get();
            if (!coupon.getIsActive()) {
                throw new UnProcessableEntityException("This coupon is expired.");
            }

            usersCoupon.setIsRedeemed(Boolean.TRUE);
            userCouponRepository.save(usersCoupon);
            return GetCouponDto.builder()
                    .couponId(usersCoupon.getId())
                    .userId(usersCoupon.getUserId().toString())
                    .code(coupon.getCode())
                    .amountOff(coupon.getAmountOff())
                    .isScratched(usersCoupon.getIsScratched())
                    .isRedeemed(usersCoupon.getIsRedeemed())
                    .isActive(coupon.getIsActive())
                    .validTillDate(coupon.getActiveDuration())
                    .build();
        } else {
            LOGGER.error("Redeem Coupon :: User want redeem un allocated coupon!");
            throw new UnProcessableEntityException("Invalid coupon!");
        }
    }

    @Override
    public List<Coupon> getAllCoupon() {
        return couponRepository.findAll();
    }

    @Override
    public void allocateCouponsToUser(List<String> couponIds, ObjectId userId) {
         if(couponIds.isEmpty()){
             LOGGER.error("Allocate coupon to user :: coupon list is empty!");
         }

         if(Objects.isNull(userId)){
             throw new UnProcessableEntityException("User id is null!");
         }
         List<UsersCoupon> usersCoupons = new ArrayList<>();
         for(String couponId : couponIds){
            UsersCoupon usersCoupon = userCouponTemplate.getByUserIdAndCouponId(userId, new ObjectId(couponId));
            if(Objects.isNull(usersCoupon)){
                usersCoupon.setUserId(userId);
                usersCoupon.setCouponId(new ObjectId(couponId));
                usersCoupon.setIsScratched(Boolean.FALSE);
                usersCoupon.setIsRedeemed(Boolean.FALSE);
                usersCoupons.add(usersCoupon);
            }
            else{
                LOGGER.info("This coupon is already there.");
            }
         }
         userCouponRepository.saveAll(usersCoupons);
    }

    @Override
    public void tempAllocateToAllUser() {

        List<User> users = userRepository.findAll();

        List<Coupon> coupons = couponRepository.findAll();

        List<UsersCoupon> usersCoupons = new ArrayList<>();
        for(User user: users){
        List<Integer> indexs = generateRandomIndex();
        for(Integer index : indexs){
        usersCoupons.add(UsersCoupon.builder()
                    .couponId(new ObjectId(coupons.get(index).getId()))
                    .userId(new ObjectId(user.getId()))
                    .isRedeemed(Boolean.FALSE)
                    .isScratched(Boolean.FALSE)
                    .build());
        }
        }
        userCouponRepository.saveAll(usersCoupons);
    }

    private List<Integer> generateRandomIndex(){
        int min = 0;
        int max = 7;
        int count = 4;

        List<Integer> randomNumbers = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < count; i++) {
            int randomNumber = random.nextInt(max - min + 1) + min;
            randomNumbers.add(randomNumber);
        }
        return randomNumbers;
    }
}
