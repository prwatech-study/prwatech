package com.prwatech.courses.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document("Cart")
public class Cart {

    @Id
    private String id;
    @Field(name = "User_Id")
    private ObjectId User_Id;

    @Field(name = "Cart_Items")
    private List<Cart_Items>Cart_Items;


    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    public static class Cart_Items{
        private ObjectId Course_Id;
        private String Course_Type;
        private String Purchase_Type;

    }
}
