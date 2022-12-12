package io.gingersnapproject.data;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

public class Car {

    @Id
    @GeneratedValue
    Long id;

    @Column
    String model;

    @Column
    String brand;
}
