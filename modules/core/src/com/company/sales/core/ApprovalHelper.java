package com.company.sales.core;

import com.company.sales.entity.Order;
import com.company.sales.entity.OrderLine;
import com.company.sales.entity.Product;
import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Persistence;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.global.Metadata;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component("sales_ApprovalHelper")
public class ApprovalHelper {

    @Inject
    private Persistence persistence;
    @Inject
    private Metadata metadata;

    public void updateLines(UUID entityId) {
        try (Transaction tx = persistence.getTransaction()) {
            EntityManager em = persistence.getEntityManager();
            Order order = em.find(Order.class, entityId);
            if (order != null) {
                List<OrderLine> lines = order.getLines();
                OrderLine newLine = metadata.create(OrderLine.class);
                Product product = metadata.create(Product.class);
                product.setName("Manager tax");
                product.setPrice(BigDecimal.ONE);
                em.persist(product);
                newLine.setProduct(product);
                newLine.setQuantity(BigDecimal.ONE);
                newLine.setOrder(order);
                em.persist(newLine);
                lines.add(newLine);
                order.setLines(lines);
            }
            tx.commit();
        }
    }
}
