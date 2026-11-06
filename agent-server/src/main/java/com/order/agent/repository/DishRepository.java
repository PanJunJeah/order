package com.order.agent.repository;

import com.order.agent.domain.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 菜品仓储。
 */
public interface DishRepository extends JpaRepository<Dish, Long> {

    List<Dish> findAllByOrderByIdAsc();

    @Query("select max(d.id) from Dish d")
    Optional<Long> findMaxId();
}
