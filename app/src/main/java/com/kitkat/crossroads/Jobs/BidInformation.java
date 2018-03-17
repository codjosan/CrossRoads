package com.kitkat.crossroads.Jobs;

import java.io.Serializable;

/**
 * Created by om25_000 on 01/03/2018.
 */

public class BidInformation implements Serializable
{

    public String userID, userBid, jobID;

    public BidInformation()
    {
    }

    public BidInformation(String userID, String userBid, String jobID)
    {
        this.userID = userID;
        this.userBid = userBid;
        this.jobID = jobID;
    }


    public String getUserID()
    {
        return userID;
    }

    public String getUserBid()
    {
        return userBid;
    }

    public void setUserID(String userID)
    {
        this.userID = userID;
    }

    public void setUserBid(String userBid)
    {
        this.userBid = userBid;
    }

    public String getWholeString()
    {
        return userID + userBid;
    }

    public String getJobID() {
        return jobID;
    }

    public void setJobID(String jobID) {
        this.jobID = jobID;
    }
}
