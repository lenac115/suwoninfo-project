package com.main.suwoninfo;
import java.util.Random;

public class etudier {

    public static void main(String args[]) {

            System.out.println(solution(12, 18));
    }

    public double solution(int[] arr) {
        int max1 = Integer.MAX_VALUE;
        int max2 = Integer.MAX_VALUE;

        int recentMax = 0;
        int latestMax = recentMax;
        for (int i =0; i<arr.length; i++){
            if(i > recentMax) {
                latestMax = recentMax;
                recentMax = i;
            }
        }
        return recentMax - latestMax;
    }
}
