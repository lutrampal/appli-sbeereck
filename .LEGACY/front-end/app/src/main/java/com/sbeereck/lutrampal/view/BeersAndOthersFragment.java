package com.sbeereck.lutrampal.view;


import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Toast;

import com.sbeereck.lutrampal.controller.ProductController;
import com.sbeereck.lutrampal.controller.RESTDataManager;
import com.sbeereck.lutrampal.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 */
public class BeersAndOthersFragment extends GeneralMainViewFragment
        implements OnOkButtonClickListener<Product> {

    private List<Product> products = new ArrayList<>();
    private ProductController controller;

    public BeersAndOthersFragment() {
        // Required empty public constructor
    }

    @Override
    public void onOkButtonClick(Product product, Boolean wasEditing) {
        if (wasEditing) {
            products.remove(product);
        }
        products.add(product);
        Collections.sort(products);
        ((BaseAdapter) mListview.getAdapter()).notifyDataSetChanged();
    }

    private class GetAllProductsTask extends AsyncTaskWithLoadAnimation<Void, Integer, List<Product>> {

        private Exception e = null;

        public GetAllProductsTask(Context context) {
            super(context);
        }

        @Override
        protected List<Product> doInBackground(Void ... voids) {
            addTaskToRunningAsyncTasks(this);
            List<Product> products = null;
            try {
                products = controller.getAllProducts();
            } catch (Exception e) {
                this.e = e;
                products = new ArrayList<>();
            }
            return products;
        }

        @Override
        protected void onPostExecute(List<Product> products) {
            super.onPostExecute(products);
            if (e != null) {
                Toast.makeText(mActivity.getApplicationContext(),
                        getString(R.string.products_loading_error) + " : " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
            BeersAndOthersFragment.this.products.clear();
            BeersAndOthersFragment.this.products.addAll(products);
            ((BaseAdapter) mListview.getAdapter()).notifyDataSetChanged();
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_beers_and_others, container, false);
        super.onCreateView(inflater, container, savedInstanceState);
        mListview.setAdapter(new ProductListItemAdapter(getActivity(), products));
        mActivity.getSupportActionBar().setTitle(R.string.beer_and_other_fragment_name);
        mFabAdd.setOnClickListener(getFabAddClickListener());
        RESTDataManager dataManager = RESTDataManagerSingleton
                .getDataManager(getActivity());
        if (dataManager != null) {
            controller = new ProductController(dataManager);
            new GetAllProductsTask(getActivity()).execute();
        } else {
            mFabAdd.setEnabled(false);
        }
        return view;
    }

    private View.OnClickListener getFabAddClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NewProductDialogFragment dialog = new NewProductDialogFragment();
                Bundle args = new Bundle();
                args.putBoolean("isEditDialog", false);
                dialog.setArguments(args);
                dialog.setOnOkButtonClickListener(BeersAndOthersFragment.this);
                dialog.show(mActivity.getSupportFragmentManager(),
                        "NewProductDialogFragment");
            }
        };
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        if (v.getId() == R.id.main_listview) {
            inflater.inflate(R.menu.menu_delete_edit, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.edit:
                editProduct(info.position);
                return true;
            case R.id.delete:
                // user wants to delete a product
                deleteProduct(info.position);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void editProduct(int position) {
        Product productToEdit = ((ProductListItemAdapter) mListview.getAdapter())
                .getFilteredProducts().get(position);
        NewProductDialogFragment dialog = new NewProductDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable("product", productToEdit);
        dialog.setArguments(args);
        dialog.setOnOkButtonClickListener(BeersAndOthersFragment.this);
        dialog.show(mActivity.getSupportFragmentManager(),
                "EditProductDialogFragment");
    }

    private class DeleteProductTask extends AsyncTask<Void, Integer, Void> {

        private Exception e = null;
        private Product selectedProduct;
        private int selectedProductIdx;

        public DeleteProductTask(Product selectedProduct, int selectedProductIdx) {
            this.selectedProduct = selectedProduct;
            this.selectedProductIdx = selectedProductIdx;
        }

        @Override
        protected Void doInBackground(Void ... voids) {
            try {
                controller.deleteProduct(selectedProduct.getId());
            } catch (Exception e) {
                this.e = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void voidObj) {
            if (e != null) {
                Toast.makeText(mActivity.getApplicationContext(),
                        getString(R.string.product_delete_error) + " : " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            List<Product> filteredProducts = ((ProductListItemAdapter) mListview.getAdapter())
                    .getFilteredProducts();
            filteredProducts.remove(selectedProductIdx);
            products.remove(selectedProduct);
            ((BaseAdapter) mListview.getAdapter()).notifyDataSetChanged();
        }
    }

    private void deleteProduct(final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        Dialog d = builder.setMessage(R.string.delete_product_confirm)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked delete button
                        List<Product> filteredProducts = ((ProductListItemAdapter) mListview.getAdapter())
                                .getFilteredProducts();
                        Product productToRemove = filteredProducts.get(position);
                        new DeleteProductTask(productToRemove, position).execute();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // user clicked cancel button
                    }
                })
                .create();
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.show();
    }
    
}
