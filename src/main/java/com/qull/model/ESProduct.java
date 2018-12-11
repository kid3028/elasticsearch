package com.qull.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author kzh
 * @Description
 * @Date 2018/12/7 9:45
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ESProduct {
    private String fId;

    private String fProducerId;

    private String fName;

    private String fSpell;

    private String fCaption;

    private String fUseType;

    private String fBrand;

    private String fSunmmary;

    private String fDesc;

    private String fState;

    private Double minBuyPrice;

    private Double maxBuyPrice;

    private Integer fTotalCount;

    private Integer fTotalSales;

    private Integer maxSale;

    private Integer minSale;

    private LocalDateTime fTime;

    private String saleSort;

    private String priceSort;

}
