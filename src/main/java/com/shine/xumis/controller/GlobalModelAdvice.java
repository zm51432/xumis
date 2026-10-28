package com.shine.xumis.controller;

import com.shine.xumis.model.Category;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Arrays;
import java.util.List;

/**
 * 全局模型增强：为所有页面提供店铺名与分类列表（导航/页脚共用）
 */
@ControllerAdvice
public class GlobalModelAdvice {

    public static final String STORE_NAME = "shine星心";
    public static final String STORE_NAME_EN = "shine Star-Heart";

    @ModelAttribute
    public void populateGlobal(Model model) {
        model.addAttribute("storeName", STORE_NAME);
        model.addAttribute("storeNameEn", STORE_NAME_EN);
        model.addAttribute("categories", Arrays.asList(Category.values()));
    }
}
