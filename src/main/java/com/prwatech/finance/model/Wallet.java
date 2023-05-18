package com.prwatech.finance.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "Wallet")
public class Wallet {

  @Id private String id;

  @Field(value = "User_Id")
  @DBRef
  private ObjectId User_Id;

  @Field(value = "My_Cash")
  private Integer My_Cash;

  @Field(value = "My_Rewards")
  private Integer My_Rewards;
}
