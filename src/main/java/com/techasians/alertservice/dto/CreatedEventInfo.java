package com.techasians.alertservice.dto;

/**
 * Thông tin sự kiện được tự động tạo mới trong quá trình import Excel.
 * Trả về trong ImportResultDTO để người dùng biết những sự kiện nào vừa được tạo.
 */
public class CreatedEventInfo {

    private Integer id;
    private String name;

    public CreatedEventInfo(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
