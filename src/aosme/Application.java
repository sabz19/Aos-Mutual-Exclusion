package aosme;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Application {
    
    public Application(int id, int meanIRD, int meanCSET, int requestnumber){
        
        int count = 0;
        try{
            /*
            Generating critical section request
            */
            while (count < requestnumber){
                Kernel.csEnter(id);
                // sleep for CS execution time
                TimeUnit.MILLISECONDS.sleep((long) generatenumber(meanCSET));
                Kernel.csExit(id);
                count++;
                // sleep for inter-request delay time
                TimeUnit.MILLISECONDS.sleep((long) generatenumber(meanIRD));
            }
            
            Kernel.appDone(id);
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    
    /*
    Function for random variables with exponential probability distribution
    */
    private double generatenumber(int mean){
        
        Random random = new Random();
        double x = random.nextDouble();
        double lambda = 1.0/mean;
        return Math.log(1-x)/(-lambda);
    }
    
    public static void main(String args[]){
        
        int node_id = Integer.parseInt(args[0]);
        int meanIRD = Integer.parseInt(args[1]);
        int meanCSET = Integer.parseInt(args[2]);
        int requestnumber = Integer.parseInt(args[3]);
        
        Application app = new Application(node_id, meanIRD, meanCSET, requestnumber);
    }
}
