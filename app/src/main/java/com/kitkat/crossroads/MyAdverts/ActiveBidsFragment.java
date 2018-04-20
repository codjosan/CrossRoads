package com.kitkat.crossroads.MyAdverts;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.kitkat.crossroads.Jobs.PostAnAdvertFragment;
import com.kitkat.crossroads.Payment.ConfigPaypal;
import com.kitkat.crossroads.ExternalClasses.DatabaseConnections;
import com.kitkat.crossroads.Jobs.UserBidInformation;
import com.kitkat.crossroads.R;
import com.paypal.android.sdk.payments.PayPalConfiguration;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PayPalService;
import com.paypal.android.sdk.payments.PaymentActivity;
import com.paypal.android.sdk.payments.PaymentConfirmation;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static android.app.Activity.RESULT_OK;


public class ActiveBidsFragment extends Fragment
{
    private OnFragmentInteractionListener mListener;

    /**
     * Getting a reference to the firebase datbase area
     */
    private DatabaseReference databaseReference;

    /**
     * Getting the current user who is sign in id
     */
    private String user;

    /**
     * List view to display the information
     */
    private ListView jobListView;

    /**
     * List to store all of the the users bids
     */
    private ArrayList<UserBidInformation> jobList = new ArrayList<>();
    private double commisionAmount;
    private double totalAmount;
    private BigDecimal totalDecimal;

    private int pos;

    public static final int PAYPAL_REQUEST_CODE = 7171;
    public static final PayPalConfiguration config = new PayPalConfiguration().environment(PayPalConfiguration.ENVIRONMENT_SANDBOX).clientId(ConfigPaypal.PAYPAL_CLIENT_ID); // Test Mode

    public ActiveBidsFragment()
    {
        // Required empty public constructor
    }

    public static ActiveBidsFragment newInstance()
    {
        ActiveBidsFragment fragment = new ActiveBidsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        databaseConnections();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.fragment_active_bids, container, false);
        getViewByIds(view);
        displayUsersBidsOnAd();

        Intent intent = new Intent(getActivity(), PayPalService.class);
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config);
        getActivity().startService(intent);

        return view;
    }

    @Override
    public void onDestroyView()
    {
        getActivity().stopService(new Intent(getActivity(), PayPalService.class));
        super.onDestroyView();
    }

    @Override
    public void onDestroy()
    {
        getActivity().stopService(new Intent(getActivity(), PayPalService.class));
        super.onDestroy();
    }

    /**
     * Accessing the Firebase Database to get information and upload new information from
     */
    private void databaseConnections()
    {
        DatabaseConnections databaseConnections = new DatabaseConnections();
        databaseReference = databaseConnections.getDatabaseReference();
        user = databaseConnections.getCurrentUser();
    }

    private void getViewByIds(View view)
    {
        jobListView = view.findViewById(R.id.jobListView1);
    }

    /**
     * Getting all arguments from the bundle that was passed across
     *
     * @return jobInformation
     */
    private String getBundleInformation()
    {
        Bundle bundle = getArguments();
        return (String) bundle.getSerializable("JobId");
    }

    /**
     * Display all of the bids received on the advert and allow the user to accept bid
     */
    private void displayUsersBidsOnAd()
    {
        final String jobId = getBundleInformation();

        databaseReference.child("Bids").child(jobId).addValueEventListener(new ValueEventListener()
        {
            @Override
            public void onDataChange(final DataSnapshot dataSnapshot)
            {
                for (final DataSnapshot ds : dataSnapshot.getChildren())
                {
                    boolean active = ds.child("active").getValue(boolean.class);
                    if (active)
                    {
                        final UserBidInformation bid = ds.getValue(UserBidInformation.class);
                        bid.setJobID(jobId);
                        jobList.add(bid);
                    }
                }

                final MyCustomAdapter myCustomAdapter = new MyCustomAdapter();
                myCustomAdapter.addArray(jobList);
                jobListView.setAdapter(myCustomAdapter);

                jobListView.setOnItemClickListener(new AdapterView.OnItemClickListener()
                {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l)
                    {
                        pos = position;
                        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                        View mView = getLayoutInflater().inflate(R.layout.popup_accept_user_bid, null);

                        alertDialog.setTitle("Accept Bid?");
                        alertDialog.setView(mView);
                        final AlertDialog dialog = alertDialog.create();
                        dialog.show();

                        TextView textViewName = mView.findViewById(R.id.textName);
                        final RatingBar ratingBar = mView.findViewById(R.id.ratingBarSeeFeedback);
                        TextView textViewBid = mView.findViewById(R.id.textBid);
                        TextView textViewCommission = mView.findViewById(R.id.textCommission);
                        TextView textViewTotal = mView.findViewById(R.id.textTotal);
                        Button payPal = mView.findViewById(R.id.acceptBidButton);

                        textViewName.setText(jobList.get(position).getFullName());
                        BigDecimal decimal = new BigDecimal(Double.parseDouble(jobList.get(position).getUserBid()));
                        decimal = decimal.setScale(2, RoundingMode.CEILING);
                        textViewBid.setText("£" + decimal);

                        String userBidBefore = jobList.get(position).getUserBid();

                        totalAmount = decimal.longValue();

                        if (totalAmount < 20.00 || totalAmount < 20.0 || totalAmount < 20)
                        {
                            commisionAmount = 1.00;
                            decimal = new BigDecimal(1.00);
                            decimal = decimal.setScale(2, RoundingMode.CEILING);
                            textViewCommission.setText("£" + decimal);
                        } else
                        {
                            double commission = Long.parseLong(userBidBefore);
                            commission = (double) (commission * 0.05);
                            Math.round(commission);
                            decimal = new BigDecimal(commission);
                            decimal = decimal.setScale(2, RoundingMode.CEILING);
                            commisionAmount = decimal.doubleValue();
                            textViewCommission.setText("£" + decimal);
                        }

                        // Convert userBid from database into long. user bid
                        long userBidBef = Long.parseLong(userBidBefore);
                        Math.round(userBidBef);
                        BigDecimal decimal1 = new BigDecimal(userBidBef);

                        decimal = new BigDecimal(commisionAmount);
                        decimal = decimal.add(decimal1);
                        totalDecimal = decimal.setScale(2, RoundingMode.CEILING);
                        textViewTotal.setText("£" + totalDecimal);

                        databaseReference.child("Ratings").child(jobList.get(position).getUserID()).addValueEventListener(new ValueEventListener()
                        {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot)
                            {
                                long totalRating = 0;
                                long counter = 0;
                                // Iterate through entire bids table
                                for (DataSnapshot ds : dataSnapshot.getChildren())
                                {
                                    long rating = ds.child("starReview").getValue(long.class);

                                    totalRating += rating;
                                    counter++;

                                    totalRating = totalRating / counter;

                                    int usersRating = Math.round(totalRating);
                                    ratingBar.setNumStars(usersRating);
                                    ratingBar.getNumStars();
                                    Drawable drawable = ratingBar.getProgressDrawable();
                                    drawable.setColorFilter(Color.parseColor("#cece63"), PorterDuff.Mode.SRC_ATOP);
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError)
                            {

                            }
                        });

                        payPal.setOnClickListener(new View.OnClickListener()
                        {
                            @Override
                            public void onClick(View v)
                            {
                                dialog.dismiss();
                                processPayment();
                            }
                        });
                    }
                });
            }

            @Override
            public void onCancelled(DatabaseError databaseError)
            {

            }
        });
    }

    private void processPayment()
    {
        PayPalPayment payPalPayment = new PayPalPayment(new BigDecimal(totalAmount + commisionAmount), "GBP"
                , "Pay CrossRoads Commission", PayPalPayment.PAYMENT_INTENT_SALE);

        Intent intent = new Intent(getActivity(), PaymentActivity.class);
        intent.putExtra(PayPalService.EXTRA_PAYPAL_CONFIGURATION, config);
        intent.putExtra(PaymentActivity.EXTRA_PAYMENT, payPalPayment);
        startActivityForResult(intent, PAYPAL_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == PAYPAL_REQUEST_CODE)
        {
            if (resultCode == RESULT_OK)
            {
                PaymentConfirmation confirmation = data.getParcelableExtra(PaymentActivity.EXTRA_RESULT_CONFIRMATION);
                if (confirmation != null)
                {
                    try
                    {
                        databaseReference.child("Jobs").child(getBundleInformation()).child("courierID").setValue(jobList.get(pos).getUserID());
                        databaseReference.child("Jobs").child(getBundleInformation()).child("jobStatus").setValue("Active");
                        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
                        fragmentTransaction.replace(R.id.content, new PostAnAdvertFragment()).commit();

                        String paymentDetails = confirmation.toJSONObject().toString(4);
                        JSONObject jsonObject = new JSONObject(paymentDetails);
                        JSONObject jsonObject1 = jsonObject.getJSONObject("response");

                        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity(), R.style.MyDialogTheme);
                        View mView = getLayoutInflater().inflate(R.layout.popup_payment_successful, null);

                        alertDialog.setTitle("Payment Successful");
                        alertDialog.setNegativeButton("Close", new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.dismiss();
                            }
                        });
                        alertDialog.setView(mView);
                        final AlertDialog dialog = alertDialog.create();
                        dialog.show();

                        TextView textViewId = mView.findViewById(R.id.textId);
                        TextView textViewAmount = mView.findViewById(R.id.textAmount);
                        TextView textViewStatus = mView.findViewById(R.id.textStatus);

                        textViewStatus.setText(jsonObject1.getString("state"));
                        textViewAmount.setText("£" + totalDecimal);
                        textViewId.setText(jsonObject1.getString("id"));
                    } catch (JSONException e)
                    {
                        e.printStackTrace();
                    }
                }
            } else if (resultCode == Activity.RESULT_CANCELED)
            {
                Toast.makeText(getActivity(), "Cancel", Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == PaymentActivity.RESULT_EXTRAS_INVALID)
        {
            Toast.makeText(getActivity(), "Invalid", Toast.LENGTH_SHORT).show();
        }
    }

    public class MyCustomAdapter extends BaseAdapter
    {

        private ArrayList<UserBidInformation> mData = new ArrayList();
        private ArrayList<UserBidInformation> mDataOrig = new ArrayList();

        private LayoutInflater mInflater;

        public MyCustomAdapter()
        {
            if (isAdded())
            {
                mInflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            }
        }

        public void addItem(final UserBidInformation item)
        {
            mData.add(item);
            mDataOrig.add(item);
        }


        public void addArray(final ArrayList<UserBidInformation> j)
        {
            mData = j;
            mDataOrig = j;
        }


        @Override
        public void registerDataSetObserver(DataSetObserver observer)
        {

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer)
        {

        }

        @Override
        public int getCount()
        {
            return mData.size();
        }

        @Override
        public Object getItem(int position)
        {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return 0;
        }

        @Override
        public boolean hasStableIds()
        {
            return false;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent)
        {
            final MyCustomAdapter.GroupViewHolder holder;
            if (convertView == null)
            {
                convertView = mInflater.inflate(R.layout.job_info_list_bid, null);
                holder = new MyCustomAdapter.GroupViewHolder();

                holder.textViewName = convertView.findViewById(R.id.textName);
                holder.textViewBid = convertView.findViewById(R.id.textBid);
                holder.ratingBarSeeFeedback = convertView.findViewById(R.id.ratingBarSeeFeedback);
                holder.ratingNoFeedback = convertView.findViewById(R.id.ratingNoFeedback);
                holder.ratingNoFeedback.setVisibility(View.GONE);

                convertView.setTag(holder);
            } else
            {
                holder = (MyCustomAdapter.GroupViewHolder) convertView.getTag();
            }

            double userBid = Double.parseDouble(mData.get(position).getUserBid());
            BigDecimal decimal = new BigDecimal(userBid);
            decimal = decimal.setScale(2, RoundingMode.CEILING);

            holder.textViewBid.setText("£" + decimal);

            databaseReference.child("Ratings").child(mData.get(position).getUserID()).addValueEventListener(new ValueEventListener()
            {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot)
                {
                    long totalRating = 0;
                    long counter = 0;
                    // Iterate through entire bids table
                    if (dataSnapshot.hasChildren())
                    {
                        for (DataSnapshot ds : dataSnapshot.getChildren())
                        {
                            long rating = ds.child("starReview").getValue(long.class);

                            totalRating += rating;
                            counter++;

                            totalRating = totalRating / counter;

                            int usersRating = Math.round(totalRating);
                            holder.ratingBarSeeFeedback.setNumStars(usersRating);
                            holder.ratingBarSeeFeedback.getNumStars();
                            Drawable drawable = holder.ratingBarSeeFeedback.getProgressDrawable();
                            drawable.setColorFilter(Color.parseColor("#cece63"), PorterDuff.Mode.SRC_ATOP);
                        }
                    } else
                    {
                        holder.ratingNoFeedback.setText("No Ratings For User");
                        holder.ratingNoFeedback.setVisibility(View.VISIBLE);
                        holder.ratingBarSeeFeedback.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError)
                {

                }
            });


            holder.textViewName.setText(mData.get(position).getFullName());
            return convertView;
        }

        @Override
        public boolean areAllItemsEnabled()
        {
            return false;
        }

        @Override
        public boolean isEmpty()
        {
            return false;
        }

        public class GroupViewHolder
        {
            public TextView textViewName;
            public TextView textViewBid;
            public RatingBar ratingBarSeeFeedback;
            public TextView ratingNoFeedback;
        }

        public void filter(String charText)
        {

            ArrayList<UserBidInformation> jobs = new ArrayList<UserBidInformation>();
            ArrayList<UserBidInformation> jA = new ArrayList<UserBidInformation>();
            charText = charText.toLowerCase(Locale.getDefault());

            if (charText.length() == 0)
            {
                mData = mDataOrig;
            } else
            {

                for (UserBidInformation j : mDataOrig)
                {
                    if (j.getWholeString().toLowerCase(Locale.getDefault()).contains(charText))
                    {
                        jobs.add(j);
                        jA.add(j);
                    } else
                    {
                        jA.add(j);
                    }
                }
                mData.clear();
                mData = jobs;
                mDataOrig = jA;
            }

            notifyDataSetChanged();
        }
    }

    public void onButtonPressed(Uri uri)
    {
        if (mListener != null)
        {
            mListener.onFragmentInteraction(uri);
        }
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener)
        {
            mListener = (OnFragmentInteractionListener) context;
        } else
        {
        }
    }

    @Override
    public void onDetach()
    {
        super.onDetach();
        mListener = null;
    }

    public interface OnFragmentInteractionListener
    {
        void onFragmentInteraction(Uri uri);
    }
}
