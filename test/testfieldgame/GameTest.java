package testfieldgame;

import java.util.ArrayList;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;


/**
 *
 * See test methods. There is a test with fixed amount of threads in hte pool 
 * and a test with encreasing amount of threads.
 * <p>
 * @author Vladislav Ustinov
 * @version 1.0
 */
public class GameTest extends Assert {
    
    /**
     *
     */
    public GameTest() {
    }
    
    /**
     *
     */
    @BeforeClass
    public static void setUpClass() {
    }
    
    /**
     *
     */
    @AfterClass
    public static void tearDownClass() {
    }
    
    /**
     *
     */
    @Before
    public void setUp() {
    }
    
    /**
     *
     */
    @After
    public void tearDown() {
    }

    /**
     * Test of {@link Game} class productivity with respect to amount of threads.
     * The {@link Game#start} method is called in a loop in which amount of threads is being increased.
     */
    @Test
    public void testProductivityThreadsAmount() {
        System.out.println("Productivity test");
        System.out.println("Number of cores = " + Runtime.getRuntime().availableProcessors());

        int INITIAL_NUM_POINTS = 50, FIELD_LENGTH = 50;
        int BEFORE_TEST_RUNS = 100;

        for (int NUM_THREADS = 1; NUM_THREADS <= 5; NUM_THREADS++) {

            long timeDuration = testFixedNumThreads(INITIAL_NUM_POINTS, FIELD_LENGTH, NUM_THREADS, BEFORE_TEST_RUNS);

            System.out.print("Threads = " + NUM_THREADS + " ; ");
            System.out.println("Milliseconds = " + timeDuration / 1000000.0);
        }
    }

    /**
     * Test of {@link Game} class productivity
     * when number of threads, length of data field and moving points (INITIAL_NUM_POINTS)
     * are all fixed values set via corresponding parameters.
     * <p>
     * Asserts that intial global field is equal to the resulting global field. 
     * This should be true, because of periodic boundary condition applied.
     * It depends on our choice of NUM_ITERATIONS = FIELD_LENGTH*NUM_THREADS in {@link Game#Game}.
     * <p>
     * The {@link Game#start} method is called when parameters are given.
     * @param INITIAL_NUM_POINTS is amount of "ones" in the field.
     * @param FIELD_LENGTH is length of each quadrant, which is smaller piece of global 2d array field.
     * @param NUM_THREADS is amount of threads in the pool.  
     * @param BEFORE_TEST_RUNS is how many times we should run the test in a loop so that JVM optimizes it well enough.
     * 
     * @return timeDuration, i.e. time of the last call of {@link Game#start} in nano seconds.
     */
    static public long testFixedNumThreads(int INITIAL_NUM_POINTS, int FIELD_LENGTH, int NUM_THREADS, int BEFORE_TEST_RUNS) {
        Game game = new Game(INITIAL_NUM_POINTS, FIELD_LENGTH, NUM_THREADS);
        long timeStart = 0, timeDuration = 0;
        for (int i = 0; i < BEFORE_TEST_RUNS; i++) {

            ArrayList<int[][]> dataBefore = game.getDeepCopyAllFields();

            timeStart = System.nanoTime();
            game.start();
            timeDuration = System.nanoTime() - timeStart;

            ArrayList<int[][]> dataAfter = game.getDeepCopyAllFields();

            
            assertTrue(allDataEquals(dataBefore, dataAfter));
        }

        return timeDuration;
    }
    
    static boolean allDataEquals(ArrayList<int[][]> list1, ArrayList<int[][]> list2) {
        if (list1 == null && list2 == null) {
            return true;
        }

        if ((list1 == null && list2 != null)
                || (list1 != null && list2 == null)) {
            return false;
        }

        if (list1.size() != list2.size()) {
            return false;
        }

        for (int ind = 0; ind < list1.size(); ind++) {
            int[][] ar1 = list1.get(ind);
            int[][] ar2 = list2.get(ind);

            for (int i = 0; i < ar1.length; i++) {
                for (int j = 0; j < ar1[0].length; j++) {
                    if (ar1[i][j] != ar2[i][j]) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
