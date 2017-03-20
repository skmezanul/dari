package com.psddev.dari.elasticsearch;

import com.psddev.dari.db.Query;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import static org.junit.Assert.assertTrue;

public class StateElasticTest extends AbstractElasticTest {

    @After
    public void deleteModels() {

        Query.from(UuidElasticIndexModel.class).deleteAll();
    }

    @Test
    public void concurrentResolveReferences() throws Exception {

        int numThreads = 20;

        UuidElasticIndexModel model1 = new UuidElasticIndexModel();
        UuidElasticIndexModel model2 = new UuidElasticIndexModel();
        model1.setReferenceOne(model2);

        model1.save();
        model2.save();

        List<Thread> threads = new ArrayList<>();
        final CyclicBarrier gate = new CyclicBarrier(numThreads);
        final CyclicBarrier end = new CyclicBarrier(numThreads + 1);
        final List<Object> references = Collections.synchronizedList(new ArrayList<>());
        final List<String> errors = Collections.synchronizedList(new ArrayList<>());

        final UuidElasticIndexModel dbModel1 = Query.from(UuidElasticIndexModel.class).noCache().master().where("_id = ?", model1.getId()).first();

        for (int i = 0; i < numThreads; i += 1) {
            threads.add(new Thread(() -> {

                try {
                    gate.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    errors.add(e.getClass().getName() + ": " + e.getMessage());
                    return;
                }

                UuidElasticIndexModel dbModel1ReferenceOne = dbModel1.getReferenceOne();

                references.add(dbModel1ReferenceOne);

                try {
                    end.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    errors.add(e.getClass().getName() + ": " + e.getMessage());
                    return;
                }
            }));
        }

        for (Thread thread : threads) {
            thread.start();
        }

        end.await();

        if (errors.size() > 0) {
            errors.forEach(System.out::println);
            throw new Exception("Encountered " + errors.size() + " concurrency errors during test!");
        }

        assertTrue(!references.stream().anyMatch(o -> o == null));
    }
}
