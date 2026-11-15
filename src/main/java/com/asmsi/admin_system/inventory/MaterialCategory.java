package com.asmsi.admin_system.inventory;

import jakarta.persistence.*;

@Entity @Table(name = "inv_category")
public class MaterialCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 80) public String name;
    @Column(length = 300) public String description;
    public Long getId() { return id; }
}
