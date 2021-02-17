package testfieldgame;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The project is dedicated only to training in Java Concurrency. 
 * The goal was to make several threads having personal pieces of data and sending/receiving 
 * messages via BlockingQueue. Not to be bored, I emagined a game similar to famous game Life, but much simpler.
 * <p>
 * {@link Game} is main class in the project. 
 * Its main goal is to perform a game loop with digits moving across the game field (2d array) according to some rules.
 * The rules are set in {@link FieldQuadrant#applyRule} function. 
 * For now, the general rule is just to move each nonzero digit to the left in the game field.
 * <p>
 * About the game and the threads. Suppose, we have a 2d array, most of which values are zeros, 
 * except for a few values equal to one (call them "ones"). 
 * Lets divide the field into smaller 2d square arrays (quadrants), on which some threads will act.
 * The threads should move "ones" to the left each turn of the game. 
 * When any "one" reaches left boundary of its threads' field part, 
 * it should jump to the next threads' field part. 
 * If it was boundary of the most left quadrant, the "one" should jump to the most right quadrant.
 * So we have global periodic boundary condition in our field.
 * <p>
 * The threads are {@link FieldQuadrant} objects. 
 * Threads' pool is formed simply via storing them in array {@link Game#fieldQuadrantAr}.
 * Each turn of the game is performed inside of loop in {@link Game#start} function 
 * with fixed number of iterations equal to {@link Game#NUM_ITERATIONS}.
 * <p>
 * Each turn we first set up integer inside of jobsCount equal to NUM_THREADS. 
 * This means that threads have to perform at least NUM_THREADS jobs. 
 * Indeed, each thread should at least move its "ones" inside of its part of the global field ({@link FieldQuadrants#fieldQuad}).
 * Then, if some of the "ones" got to the left boundary, the threads put corresponding messages into 
 * the message queues of other threads ("ones" jump to other field parts). 
 * Before putting new message about new job, the thread increases jobsCount.
 * Then, the thread checks up its own message queue, 
 * takes messages and places new "ones" in its part of the field, if any. 
 * After doing another job, the thread decreases jobsCount.
 * The thread will wait in msgQueue.take() eternally until the main thread will interrupt it.
 * <p>
 * The main thread in {@link Game#start} function waits until other threads from the pool notify 
 * that jobsCount has been changed. When jobsCount is zero, 
 * the main thread understands that there are no more jobs to be done and
 * interrupts all the other threads in the pool. Then we're done and next turn comes.
 * <p>
 * See {@link GameTest} class for launching the productivity test of {@link Game} class. 
 * After fixed amount of iterations the game field should become the same as it was in the beginning.
 * This follows from our periodic boundary condition, and this is used to check up for games expected vs actual state in the test.
 * <p>
 * @author Vladislav Ustinov
 * @version 1.0
 */
//github repository Java-Concurrency-Hello-World-Life-Game
final public class Game {

    /**
     * jobsCount counts jobs left to do in each game loop iteration in {@link Game#start} function.  
     * New jobsCount object of Counter class is made just once in constructor of {@link Game}.
     * It will be given via final reference to all threads in the pool.     
     * jobsCount should be sycnhronized in threads manually.
     * The wait-notify clause is used to connect main thread with threads' pool.
     */
    private final Counter jobsCount;
    
    /**
     * jobsCountLock is ReentrantLock. It should be initialized in main Game class.
     */
    private final Lock jobsCountLock;
    
    /**
     * jobsCountLockCondition is a condition, made by jobsCountLock.newCondition () in main Game class.
     * It is shared among all the threads in hte pool;
     */
    private final Condition jobsCountLockCondition;
    
    /**
     * INITIAL_NUM_POINTS is amount of "ones" in the field.
     */
    private final int INITIAL_NUM_POINTS;   
    /**
     * FIELD_LENGTH is length of each quadrant, which is smaller piece of global 2d array field.
    */
    private final int FIELD_LENGTH;    
    /**
     * NUM_THREADS is amount of threads in the pool. 
     * The total number of threads in the programm is NUM_THREADS + 1.
     */
    private final int NUM_THREADS;
    /**
     * NUM_ITERATIONS is how many turns will be in the main life loop of the game.
     */
    private final int NUM_ITERATIONS;
    
    /**
     * fieldQuadrantAr is array imitating threads pool. 
     * Perhaps, ExectorService would be better choice, but for now it is as it is.
     */
    private final FieldQuadrant[] fieldQuadrantAr;
    
    /**
     * getDeepCopyAllFields is used in test to fix initial global field 
     * and then compare it with the state after all loops. They should equal each other.
     * @return ArrayList of quadrants, which all together make up the global game field.
     */
    public ArrayList<int[][]> getDeepCopyAllFields() {
        if (fieldQuadrantAr == null) {
            return null;
        }

        ArrayList<int[][]> resAr = new ArrayList<>();
        
        for (FieldQuadrant quad : fieldQuadrantAr) {
            resAr.add(quad.getDeepCopyFiledQuad());
        }

        return resAr;
    }
    
    /**
     * Constructor sets up data via using new operator to all final fields, which will be not renewed
     * until the very end of the game.
     * 
     * @param INITIAL_NUM_POINTS is amount of "ones" in the field.
     * @param FIELD_LENGTH is length of each quadrant, which is smaller piece of global 2d array field.
     * @param NUM_THREADS is amount of threads in the pool.  
     */
    public Game (int INITIAL_NUM_POINTS, int FIELD_LENGTH, int NUM_THREADS) {
        this.INITIAL_NUM_POINTS = INITIAL_NUM_POINTS;
        this.FIELD_LENGTH = FIELD_LENGTH;        
        this.NUM_THREADS = NUM_THREADS;
        NUM_ITERATIONS = FIELD_LENGTH*NUM_THREADS;
        
        jobsCount = new Counter (NUM_THREADS);
        jobsCountLock = new ReentrantLock();
        jobsCountLockCondition = jobsCountLock.newCondition();
        
        fieldQuadrantAr = new FieldQuadrant [NUM_THREADS];

        int x1 = 0, x2 = FIELD_LENGTH, y1 = 0, y2 = FIELD_LENGTH;
        for (int i = 0; i < NUM_THREADS; i++) {
            int quadrantNum = i;
            int[][] fieldQuad = new int [x2-x1][y2-y1];
            fieldQuadrantAr[i] = new FieldQuadrant(jobsCountLock, jobsCountLockCondition, jobsCount, fieldQuad, x1, y1, x2, y2, quadrantNum, fieldQuadrantAr);
            x1 = x2+1; x2 = x1+FIELD_LENGTH; y1 = 0; y2 = FIELD_LENGTH;
        }
                
        setRandomInit (INITIAL_NUM_POINTS);                                
    }
    
    /**
     * reinitializeQuadrantThreads is used to restart all threads after they were interrupted in each main game loop iteration.
     */
    private void reinitializeQuadrantThreads () {
        for (int i = 0; i < fieldQuadrantAr.length; i ++)
            fieldQuadrantAr[i] = fieldQuadrantAr[i].getNewCloneWithOldLinks();        
    }
    
    /**
     * setRandomInit sets "ones" at random positions in each of field quadrants.
     * @param amountNonzero is amount of "ones" in each quadrants. They will be moved left each iteration of the main loop.
     */
    private void setRandomInit (int amountNonzero) {
        for (int i = 0; i < fieldQuadrantAr.length; i ++)
            fieldQuadrantAr[i].setRandomInit(amountNonzero);        
    }        
    
    /**
     * Just prints global field in standard output stream.
     */
    public void printQuadrants (){
        for (int j = 0; j < fieldQuadrantAr[0].getNY(); j++) {
            for (int i = 0; i < fieldQuadrantAr.length; i++) {
                fieldQuadrantAr[i].printString(j);
                System.out.print(" ");
            }

            System.out.println();
        }

        System.out.println();
    }

    /**
     * Launches main game loop. 
     * 
     * Printing is commented - you may uncomment it and see what happens at each loop iteration.
     * You may also uncomment scan.next () section and each turn wait for keyboard input to proceed.
     * If you do so, the typing "q" should quit the loop.
     */
    public void start() {
        //Scanner scan = new Scanner(System.in);
        //System.out.println("Game start!");

        int currentIteration = 0;
        
        //printQuadrants();        
        
        while (currentIteration < NUM_ITERATIONS) {
            
            //printQuadrants();
            jobsCount.set(fieldQuadrantAr.length);

            reinitializeQuadrantThreads();

            for (FieldQuadrant quad : fieldQuadrantAr) {
                quad.start();
            }

            try {
                jobsCountLock.lock();
                
                try {
                    while (jobsCount.get() > 0) {
                        jobsCountLockCondition.await();
                    }
                } finally {
                    jobsCountLock.unlock();
                }

                if (jobsCount.get() < 0) {
                    throw new Exception("MainStream: jobsCount.get() < 0");
                }
                /*synchronized (jobsCount) { // very important that get is aslo in synchronized block!
                    //very important that while has condition
                    while (jobsCount.get() > 0) {
                        jobsCount.wait();
                    }
                    
                    if (jobsCount.get() < 0) {
                        throw new Exception("MainStream: jobsCount.get() < 0");
                    }
                }*/
            } catch (InterruptedException ex) {
                Logger.getLogger(Game.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                Logger.getLogger(Game.class.getName()).log(Level.SEVERE, null, ex);
            }

            for (FieldQuadrant quad : fieldQuadrantAr) {
                quad.interrupt();
            }

            //System.out.println("jobsCount.get() = " + jobsCount.get() + ", press any key or q!");

            /*try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Logger.getLogger(Game.class.getName()).log(Level.SEVERE, null, ex);
            }*/
            
            currentIteration ++;
/*            String ans = scan.next();
            if (ans.equals("q")) {
                break;
            }*/
        }

        //printQuadrants();
        
        //System.out.println("Game ended");
    }

    /**
     * Prints String s in System.out in a synchronized way. 
     * Nobody is going to manipulate System.out meantime, so this is considered to be thread safe.
     * 
     * However for now, the printing is commented. 
     * The function is used inside of FieldQuadrants for debug prints.
     * So if you uncomment the code inside, the debug information will print.
     * 
     * Hm, probably I'd better print to Logger.getLogger (). Forget about it :)
     * 
     * @param s is a string to be printed.
     */
    public static void safePrintln(String s) {
        //synchronized (System.out) { //safe as long as nobody changes System.out via System.setOut
        //    System.out.println(s);
        //}
    }
}
//LockCond branch

