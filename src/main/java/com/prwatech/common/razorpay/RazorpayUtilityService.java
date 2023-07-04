package com.prwatech.common.razorpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prwatech.common.configuration.AppContext;
import com.prwatech.common.razorpay.dto.CreateOrderDto;
import com.prwatech.common.razorpay.dto.RazorpayOrder;
import com.razorpay.Order;
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

            ObjectMapper mapper = new ObjectMapper();
            Order order = razorpay.orders.create(orderRequest);
            String response = mapper.writeValueAsString(order);
            RazorpayOrder razorpayOrder = mapper.readValue(response, RazorpayOrder.class);

            if(Objects.isNull(razorpayOrder)){
             LOGGER.error("Error :: Razorpay create order :: got null response with response : {}", response);
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

            ObjectMapper mapper = new ObjectMapper();
            String response = mapper.writeValueAsString(order);
            RazorpayOrder razorpayOrder = mapper.readValue(response, RazorpayOrder.class);

            if(Objects.isNull(razorpayOrder)){
                LOGGER.error("Error :: Razorpay create order :: got null response with response : {}", response);
                return null;
            }
            return razorpayOrder;

        }catch (Exception e){
            LOGGER.error("Error ::  Get Razorpay Order :: due to : {}", e.getMessage());
        }
        return null;
    }

    //webhook call to get order status.

}
