package com.prwatech.common.razorpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.razorpay.dto.CreateOrderDto;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import com.prwatech.finance.dto.RazorpayPayment;
import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import lombok.AllArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@AllArgsConstructor
@Component
public class RazorpayUtilityService {

    private final AppContext appContext;
    private final static Logger LOGGER= LoggerFactory.getLogger(RazorpayUtilityService.class);

    public RazorpayOrder createOrder(CreateOrderDto createOrderDto){
        String apiKey =appContext.getRazorpayKey();
        String secretKey =appContext.getRazorpaySecret();
        try
        {
            RazorpayClient razorpay = new RazorpayClient(apiKey,secretKey);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount",createOrderDto.getAmount()*100);
            orderRequest.put("currency",createOrderDto.getCurrency());
            orderRequest.put("receipt", createOrderDto.getReceipt());
            JSONObject notes = new JSONObject(createOrderDto.getNotes());
            orderRequest.put("notes",notes);

            Order order = razorpay.orders.create(orderRequest);

            RazorpayOrder razorpayOrder = RazorpayOrder.builder()
                    .orderId(order.get("id"))
                    .entity(order.get("entity"))
                    .amount(order.get("amount"))
                    .amountDue(order.get("amount_paid"))
                    .amountPaid(order.get("amount_due"))
                    .currency(order.get("currency"))
                    .receipt(order.get("receipt"))
                    .status(order.get("status"))
                    .attempts(order.get("attempts"))
                    .createdAt(order.get("created_at"))
                    .build();


            if(Objects.isNull(razorpayOrder)){
             LOGGER.error("Error :: Razorpay create order :: got null response with response : {}", order.toJson());
             return null;
            }
            return razorpayOrder;
        }
        catch (Exception e){
            LOGGER.error("Error :: Razorpay create order :: due to : {}", e.getMessage());
        }

        return null;
    }

    public RazorpayOrder getOrderById(String orderId){

        String apiKey =appContext.getRazorpayKey();
        String secretKey =appContext.getRazorpaySecret();

        try{
            RazorpayClient razorpay = new RazorpayClient(apiKey, secretKey);
            Order order = razorpay.orders.fetch(orderId);

            RazorpayOrder razorpayOrder = RazorpayOrder.builder()
                    .orderId(order.get("id"))
                    .entity(order.get("entity"))
                    .amount(order.get("amount"))
                    .amountDue(order.get("amount_paid"))
                    .amountPaid(order.get("amount_due"))
                    .currency(order.get("currency"))
                    .receipt(order.get("receipt"))
                    .status(order.get("status"))
                    .attempts(order.get("attempts"))
                    .createdAt(order.get("created_at"))
                    .build();

            if(Objects.isNull(razorpayOrder)){
                LOGGER.error("Error :: Razorpay create order :: got null response with response : {}", order.toJson());
                return null;
            }
            return razorpayOrder;

        }catch (Exception e){
            LOGGER.error("Error ::  Get Razorpay Order :: due to : {}", e.getMessage());
        }
        return null;
    }

    public RazorpayPayment getPaymentByPaymentId(String paymentId){
        String apiKey =appContext.getRazorpayKey();
        String secretKey =appContext.getRazorpaySecret();
        try{
            RazorpayClient razorpay = new RazorpayClient(apiKey, secretKey);

            Payment payment = razorpay.payments.fetch(paymentId);

            RazorpayPayment razorpayPayment = RazorpayPayment
                    .builder().paymentId(
                            payment.get("id"))
                    .amount(payment.get("amount"))
                    .status(payment.get("status"))
                    .orderId(payment.get("order_id"))
                    .captured(payment.get("captured")).build();

            if(Objects.isNull(razorpayPayment)){
                LOGGER.error("Error :: Razorpay get payment :: got null response with response : {}", payment.toJson());
                return null;
            }
            return razorpayPayment;

        }catch (Exception e){
            LOGGER.error("Error ::  Get Razorpay Payment :: due to : {}", e.getMessage());
        }
         return  null ;

    }

}
