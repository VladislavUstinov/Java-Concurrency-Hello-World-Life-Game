package testfieldgame;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FieldQuadrant is a Thread which operates with its own personal 2d part of the data field - fieldQuad.
 * {@link FieldQuadrant#fieldQuad} is fullfilled with zeros, except for some position, where it contains "ones".
 * The purpose of FieldQuadrant is to move "ones" to the left in {@link FieldQuadrant#fieldQuad} 2d array.
 * When yet another "one" comes to the left boundary - it goes to another FeildQuadrant via its own msgQueue.
 * Also if some other FieldQuadrants put a message in msgQueue of the current FieldQuadrant, 
 * then it will put some more "ones" in its field getting these messages for the queue.
 * <p>
 * FieldQuadrant has access to other threads of the game via array fieldQuadrantAr.
 * FieldQuadrant makes its game turn in call {@link FieldQuadrant#makeTurn} ().
 * Then it puts messages to msgQueues of other quadrants increasing jobsCount counter.
 * Here, FieldQuadrant decreases {@link FieldQuadrant#jobsCount}, because it has done its own main job.
 * Then it takes massages from its own msgQueue in call {@link FieldQuadrant#takeMessagesFromQueue} () and
 * decreases jobsCount.
 * <p>
 * When changing jobsCount, we run jobsCount.notify in order to notify main thread about amount 
 * of jobs left to perform. When jobsCount is zero, the main thread (in {@link Game} class) will interrupt all the FieldQuadrants.
 * Normally, the should just wait at thier empty queue.get() and be ready to be interrupted.
 * <p>
 * After a turn, the main thread will renew FieldQuadrant threads in a loop via {@link FieldQuadrant#getNewCloneWithOldLinks} ().
 * <p>
 * FieldQuadrant usage example is shown in {@link Game} class.
 * @author Vladislav Ustinov
 * @version 1.0
 */
final public class FieldQuadrant extends Thread {      
    /**
     * part of game field which belongs personally to FieldQuadrant thread.
     * It can be printed and returned via deepCopy.
     * Access to fieldQuad should not be given anywhere outside FieldQuadrant.
     * As it belongs to only one thread, it may be changed without synchronization
     */
    private final int[][] fieldQuad;
    
    /**
     * nX = fieldQuand.length
     * nY = fieldQuad[0].length
     */    
    private final int nX, nY;
    
    /**
     * Global coordinates of current fieldQuad in the whole game field.
     */
    private final int x1, y1, x2, y2;
    
    /**
     * array of other threads operating in a game
     */    
    final private FieldQuadrant[] fieldQuadrantAr;
    
    /**
     * quadrantNum is number of current FieldQuadrant in fieldQuadrantAr.
     */
    final int quadrantNum;

    /**
     * jobsCount is a variable shared among other FieldQuadrants in fieldQuadrantAr.
     * It only should be used in locked via jobsCountLock blocks. 
     * It should run jobsCountLockCondition.signal(), when being changed.
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
     * Queue ob messages with additional work, coming from other threads in a game.
     */
    private final LinkedBlockingQueue<Point> msgQueue = new LinkedBlockingQueue<>();

    /**
     * Access to {@link FieldQuadrant#fieldQuad} data is given only via deepCopy. 
     * It is used in tests to confirm that expected data equals actual.
     * @return deep copy of {@link FieldQuadrant#fieldQuad} 2d array.
     */
    public int[][] getDeepCopyFiledQuad() {
        if (fieldQuad == null) {
            return null;
        }

        int[][] fieldQuadCopy = new int[fieldQuad.length][fieldQuad[0].length];

        for (int i = 0; i < fieldQuad.length; i++) {
            for (int j = 0; j < fieldQuad[0].length; j++) {
                fieldQuadCopy[i][j] = fieldQuad[i][j];
            }
        }

        return fieldQuadCopy;
    }

    /**
     * Threads are dead after they were interrupted and stoped.
     * To perform new work, we make a new clone with all the same references to old objects.
     * So the thread is renewed, but the data is old.
     * Should be used carefully, so that no data leak occurs.
     * @return new FieldQuadrant with old data inside.
     */
    public FieldQuadrant getNewCloneWithOldLinks (){
                
        return new FieldQuadrant (jobsCountLock, jobsCountLockCondition, jobsCount, fieldQuad, x1, y1, x2, y2, quadrantNum, fieldQuadrantAr);        
    }
    
    public int getNY (){
        return nY;
    }
    
    /**
     * Prints j-s string of fieldQuad data field (System.out stream).
     * Used in printing of all field in Game class.
     * Not Thread Safe.
     * @param j - number of string in fieldQuad array to be printed.
     */
    public void printString (int j) {
        for (int i = 0; i < fieldQuad.length; i ++)
            System.out.print (fieldQuad[i][j] + " ");
    }
    
    /**
     * For proper independent tests we need to set initial data in a randomized way.
     * @param amountNonzero is amount of non zero (i.e. ones) elements in {@link FieldQuadrant#fieldQuad}.
     */
    public void setRandomInit (int amountNonzero) {
        if (fieldQuad == null)
            return;
        
        for (int i = 0; i < amountNonzero; i ++) {
            double x = Math.random()*(fieldQuad.length-1);
            double y = Math.random()*(fieldQuad[0].length-1);
            fieldQuad[(int)x][(int)y] = 1;
        }                        
    }
    
    /**
     * Concurrent run function actually calles makeTurn().
     */
    @Override
    public void run () {
        makeTurn ();
    }
    
    /**
     * Main function of the class. Do my own job moving "ones" to the left.
     * Then ask other threads to move "ones", which have gone out of current {@link FieldQuadrant#fieldQuad}.
     * Take the same messages coming to current threads from the others in {@link FieldQuadrant#takeMessagesFromQueue}.
     */
    public void makeTurn (){
        
        Game.safePrintln("Start of quadrant " + quadrantNum);
        //synchronized (fieldQuad) { <- nobodyelse has access, so no need for synchronization
            for (int i = 0; i < nX; i ++)
                for (int j = 0; j < nY; j ++)
                    applyRule (i,j);
        //}
        
        Game.safePrintln("Quadrant " + quadrantNum + " is taking additional msgs from queue");
        
        //synchronized (jobsCount) {
        jobsCountLock.lock();
        try{
            jobsCount.decrement();
            //jobsCount.notify();
            jobsCountLockCondition.signal();
        }finally{
            jobsCountLock.unlock();
        }
        //}
        takeMessagesFromQueue ();
    }
    
    /**
     * Takes messages from queue. The queue is a Blocking queue. 
     * When all threads are waiting in their queue.take() and 
     * jobsCount = 0, then main thread in {@link Game#start} function will interrupt all FieldQuadrants.
     * InterraptedException should be silently successfully cought inside of the function.
     */
    private void takeMessagesFromQueue (){
        try {
            while (true){
                Point pIndex = msgQueue.take();
                //synchronized (fieldQuad) {
                    fieldQuad[pIndex.x][pIndex.y] = pIndex.val;
                //}
                //synchronized (jobsCount) {
                jobsCountLock.lock();
                try{
                    jobsCount.decrement();
                    //jobsCount.notify();
                    jobsCountLockCondition.signal();
                }finally {
                    jobsCountLock.unlock();
                }
                //}
            }
        } catch (InterruptedException ex) {
            //Logger.getLogger(FieldQuadrant.class.getName()).log(Level.SEVERE, null, ex);
            Game.safePrintln("Successfully interrupted quadrant " + quadrantNum);
            interrupt ();
            //return;
        }
    }
    
    /**
     * Applies main rule of the game, i.e. moves "ones" to the left,
     * and gives jobs to other threads, when "ones" are out of fieldQuad boundaries.
     * The function is run in {@link FeildQuadrant#makeTurn} in a loop. 
     * @param i - current position in {@link FieldQuadrant#fieldQuad} 2d array.
     * @param j - current position in {@link FieldQuadrant#fieldQuad} 2d array.
     */
    private void applyRule (int i, int j) {
        if (fieldQuad[i][j] > 0) {
            int oldVal = fieldQuad[i][j];
            fieldQuad[i][j] = 0;
            if (i-1 >= 0)
                fieldQuad[i-1][j] = oldVal; //лучше пока просто налево пустить
            else {                
                if (quadrantNum == 0)
                    fieldQuadrantAr[fieldQuadrantAr.length-1].
                        setAfterMyTurnIndex(nX-1, j, oldVal);
                else 
                    fieldQuadrantAr[quadrantNum-1].setAfterMyTurnIndex(nX-1, j, oldVal);
            }
        }            
    }
    
    
    /**
     * puts new message in msgQueue. Should not be interrupted. 
     * If it was interrupted, it means some error occured.
     * @param i - position to put val in {@link FieldQuadrant#fieldQuad} 2d array.
     * @param j - position to put val in {@link FieldQuadrant#fieldQuad} 2d array.
     * @param val - normally equals to "one" in versions 1.0, 2.0
     */
    public void setAfterMyTurnIndex (int i, int j, int val) {
        try {
            //synchronized (jobsCount) {
            jobsCountLock.lock();
            try{
                jobsCount.increment();
                jobsCountLockCondition.signal();
            } finally {
                jobsCountLock.unlock();
            }
            //}

            Game.safePrintln("quadrant " + quadrantNum + " queue.put new msg");
            msgQueue.put(new Point (i,j,val));
            
        } catch (InterruptedException ex) {
            interrupt();
            Logger.getLogger(FieldQuadrant.class.getName()).log(Level.SEVERE, null, ex);            
        }
    }
        
    public FieldQuadrant(Lock jobsCountLock, Condition jobsCountLockCondition, Counter jobsCount, final int [][] fieldQuad, int x1, int y1, int x2, int y2, int quadrantNum, FieldQuadrant[] fieldQuadrantAr) {
        
        this.jobsCountLockCondition = jobsCountLockCondition;
        this.jobsCountLock = jobsCountLock;
        this.jobsCount = jobsCount;
        this.fieldQuad = fieldQuad;//deepCopy(fieldQuad); //new int [x2-x1][y2-y1];
        this.nX = x2-x1;
        this.nY = y2-y1;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.fieldQuadrantAr = fieldQuadrantAr;
        this.quadrantNum = quadrantNum;
    }
         
    /**
     * Class for immutable Points containing final ints {x,y,val} .
     */
    final class Point {
        final int x;
        final int y;
        final int val;

        public Point(int x, int y, int val) {
            this.x = x;
            this.y = y;
            this.val = val;
        }
    }
}

//LockCond branch
