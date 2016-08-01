//    Openbravo POS is a point of sales application designed for touch screens.
//    Copyright (C) 2007-2009 Openbravo, S.L.
//    http://www.openbravo.com/product/pos
//
//    This file is part of Openbravo POS.
//
//    Openbravo POS is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    Openbravo POS is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with Openbravo POS.  If not, see <http://www.gnu.org/licenses/>.
package com.openbravo.pos.customers;

import com.openbravo.basic.BasicException;
import com.openbravo.data.gui.ComboBoxValModel;
import com.openbravo.data.loader.QBFCompareEnum;
import com.openbravo.data.loader.SentenceList;
import com.openbravo.data.loader.TableDefinition;
import com.openbravo.data.user.EditorCreator;
import com.openbravo.data.user.ListProvider;
import com.openbravo.data.user.ListProviderCreator;
import com.openbravo.data.user.SaveProvider;
import com.openbravo.format.Formats;
import com.openbravo.pos.forms.AppLocal;
import com.openbravo.pos.forms.DataLogicSales;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author adrianromero
 */
public class JCustomerFinder extends javax.swing.JDialog implements EditorCreator {

    private CustomerInfo selectedCustomer;
    private ListProvider lpr;
    private TableDefinition tcustomer;
    private SentenceList m_sentcat = null;
    private ComboBoxValModel m_CategoryModel;
    private Object[] customerNewAfip;

    /**
     * Creates new form JCustomerFinder
     */
    private JCustomerFinder(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
    }

    /**
     * Creates new form JCustomerFinder
     */
    private JCustomerFinder(java.awt.Dialog parent, boolean modal) {
        super(parent, modal);
    }

    public static JCustomerFinder getCustomerFinder(Component parent, DataLogicCustomers dlCustomers) {
        Window window = getWindow(parent);

        JCustomerFinder myMsg;
        if (window instanceof Frame) {
            myMsg = new JCustomerFinder((Frame) window, true);
        } else {
            myMsg = new JCustomerFinder((Dialog) window, true);
        }
        myMsg.init(dlCustomers);
        myMsg.applyComponentOrientation(parent.getComponentOrientation());
        return myMsg;
    }

    public CustomerInfo getSelectedCustomer() {
        return selectedCustomer;
    }

    private void init(DataLogicCustomers dlCustomers) {

        initComponents();
        tcustomer = dlCustomers.getTableCustomers();
        DataLogicSales dlSales = new DataLogicSales();
        dlSales.init(dlCustomers.s);
        m_sentcat = dlSales.getTaxCustCategoriesList();
        m_CategoryModel = new ComboBoxValModel();
        jScrollPane1.getVerticalScrollBar().setPreferredSize(new Dimension(35, 35));

        m_jtxtTaxID.addEditorKeys(m_jKeys);
        m_jtxtSearchKey.addEditorKeys(m_jKeys);
        m_jtxtName.addEditorKeys(m_jKeys);

        m_jtxtTaxID.reset();
        m_jtxtSearchKey.reset();
        m_jtxtName.reset();

        m_jtxtTaxID.activate();

        lpr = new ListProviderCreator(dlCustomers.getCustomerList(), this);

        jListCustomers.setCellRenderer(new CustomerRenderer());

        getRootPane().setDefaultButton(jcmdOK);

        selectedCustomer = null;
    }

    public SaveProvider getSaveProvider() {
        return new SaveProvider(tcustomer);
    }

    public void search(CustomerInfo customer) {

        if (customer == null || customer.getName() == null || customer.getName().equals("")) {

            m_jtxtTaxID.reset();
            m_jtxtSearchKey.reset();
            m_jtxtName.reset();

            m_jtxtTaxID.activate();

            cleanSearch();
        } else {

            m_jtxtTaxID.setText(customer.getTaxid());
            m_jtxtSearchKey.setText(customer.getSearchkey());
            m_jtxtName.setText(customer.getName());

            m_jtxtTaxID.activate();

            executeSearch();
        }
    }

    private void cleanSearch() {
        jListCustomers.setModel(new MyListData(new ArrayList()));
    }

    public void executeSearch() {
        try {
            jListCustomers.setModel(new MyListData(lpr.loadData()));
            if (jListCustomers.getModel().getSize() > 0) {
                jListCustomers.setSelectedIndex(0);
            }
        } catch (BasicException e) {
            e.printStackTrace();
        }
    }

    public Object createValue() throws BasicException {

        Object[] afilter = new Object[6];

        // TaxID
        if (m_jtxtTaxID.getText() == null || m_jtxtTaxID.getText().equals("")) {
            afilter[0] = QBFCompareEnum.COMP_NONE;
            afilter[1] = null;
        } else {
            afilter[0] = QBFCompareEnum.COMP_RE;
            afilter[1] = "%" + m_jtxtTaxID.getText() + "%";
        }

        // SearchKey
        if (m_jtxtSearchKey.getText() == null || m_jtxtSearchKey.getText().equals("")) {
            afilter[2] = QBFCompareEnum.COMP_NONE;
            afilter[3] = null;
        } else {
            afilter[2] = QBFCompareEnum.COMP_RE;
            afilter[3] = "%" + m_jtxtSearchKey.getText() + "%";
        }

        // Name
        if (m_jtxtName.getText() == null || m_jtxtName.getText().equals("")) {
            afilter[4] = QBFCompareEnum.COMP_NONE;
            afilter[5] = null;
        } else {
            afilter[4] = QBFCompareEnum.COMP_RE;
            afilter[5] = "%" + m_jtxtName.getText() + "%";
        }

        return afilter;
    }

    private static Window getWindow(Component parent) {
        if (parent == null) {
            return new JFrame();
        } else if (parent instanceof Frame || parent instanceof Dialog) {
            return (Window) parent;
        } else {
            return getWindow(parent.getParent());
        }
    }

    private static class MyListData extends javax.swing.AbstractListModel {

        private java.util.List m_data;

        public MyListData(java.util.List data) {
            m_data = data;
        }

        public Object getElementAt(int index) {
            return m_data.get(index);
        }

        public int getSize() {
            return m_data.size();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        m_jKeys = new com.openbravo.editor.JEditorKeys();
        jPanel3 = new javax.swing.JPanel();
        jPanel5 = new javax.swing.JPanel();
        jPanel7 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        m_jtxtName = new com.openbravo.editor.JEditorString();
        jLabel6 = new javax.swing.JLabel();
        m_jtxtSearchKey = new com.openbravo.editor.JEditorString();
        jLabel7 = new javax.swing.JLabel();
        m_jtxtTaxID = new com.openbravo.editor.JEditorString();
        jPanel6 = new javax.swing.JPanel();
        jButton1 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jListCustomers = new javax.swing.JList();
        jPanel8 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jcmdOK = new javax.swing.JButton();
        jcmdCancel = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(AppLocal.getIntString("form.customertitle")); // NOI18N

        jPanel2.setLayout(new java.awt.BorderLayout());
        jPanel2.add(m_jKeys, java.awt.BorderLayout.NORTH);

        getContentPane().add(jPanel2, java.awt.BorderLayout.LINE_END);

        jPanel3.setLayout(new java.awt.BorderLayout());

        jPanel5.setLayout(new java.awt.BorderLayout());

        jLabel5.setText(AppLocal.getIntString("label.prodname")); // NOI18N

        jLabel6.setText(AppLocal.getIntString("label.searchkey")); // NOI18N

        jLabel7.setText(AppLocal.getIntString("label.taxid")); // NOI18N

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(m_jtxtTaxID, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(m_jtxtName, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(m_jtxtSearchKey, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(31, Short.MAX_VALUE))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel7)
                    .addComponent(m_jtxtTaxID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel6)
                    .addComponent(m_jtxtSearchKey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel5)
                    .addComponent(m_jtxtName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel5.add(jPanel7, java.awt.BorderLayout.CENTER);

        jButton1.setText(AppLocal.getIntString("button.clean")); // NOI18N
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });
        jPanel6.add(jButton1);

        jButton3.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/openbravo/images/launch.png"))); // NOI18N
        jButton3.setText(AppLocal.getIntString("button.executefilter")); // NOI18N
        jButton3.setFocusPainted(false);
        jButton3.setFocusable(false);
        jButton3.setRequestFocusEnabled(false);
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });
        jPanel6.add(jButton3);

        jButton2.setText("Buscar AFIP");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        jPanel6.add(jButton2);

        jPanel5.add(jPanel6, java.awt.BorderLayout.SOUTH);

        jPanel3.add(jPanel5, java.awt.BorderLayout.PAGE_START);

        jPanel4.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        jPanel4.setLayout(new java.awt.BorderLayout());

        jListCustomers.setFocusable(false);
        jListCustomers.setRequestFocusEnabled(false);
        jListCustomers.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jListCustomersMouseClicked(evt);
            }
        });
        jListCustomers.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                jListCustomersValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(jListCustomers);

        jPanel4.add(jScrollPane1, java.awt.BorderLayout.CENTER);

        jPanel3.add(jPanel4, java.awt.BorderLayout.CENTER);

        jPanel8.setLayout(new java.awt.BorderLayout());

        jcmdOK.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/openbravo/images/button_ok.png"))); // NOI18N
        jcmdOK.setText(AppLocal.getIntString("Button.OK")); // NOI18N
        jcmdOK.setEnabled(false);
        jcmdOK.setFocusPainted(false);
        jcmdOK.setFocusable(false);
        jcmdOK.setMargin(new java.awt.Insets(8, 16, 8, 16));
        jcmdOK.setRequestFocusEnabled(false);
        jcmdOK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jcmdOKActionPerformed(evt);
            }
        });
        jPanel1.add(jcmdOK);

        jcmdCancel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/openbravo/images/button_cancel.png"))); // NOI18N
        jcmdCancel.setText(AppLocal.getIntString("Button.Cancel")); // NOI18N
        jcmdCancel.setFocusPainted(false);
        jcmdCancel.setFocusable(false);
        jcmdCancel.setMargin(new java.awt.Insets(8, 16, 8, 16));
        jcmdCancel.setRequestFocusEnabled(false);
        jcmdCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jcmdCancelActionPerformed(evt);
            }
        });
        jPanel1.add(jcmdCancel);

        jPanel8.add(jPanel1, java.awt.BorderLayout.LINE_END);

        jPanel3.add(jPanel8, java.awt.BorderLayout.SOUTH);

        getContentPane().add(jPanel3, java.awt.BorderLayout.CENTER);

        setSize(new java.awt.Dimension(613, 610));
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents
    private void jcmdOKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jcmdOKActionPerformed

        selectedCustomer = (CustomerInfo) jListCustomers.getSelectedValue();
        dispose();

    }//GEN-LAST:event_jcmdOKActionPerformed

    private void jcmdCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jcmdCancelActionPerformed

        dispose();

    }//GEN-LAST:event_jcmdCancelActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed

        executeSearch();

    }//GEN-LAST:event_jButton3ActionPerformed

    private void jListCustomersValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_jListCustomersValueChanged

        jcmdOK.setEnabled(jListCustomers.getSelectedValue() != null);

    }//GEN-LAST:event_jListCustomersValueChanged

    private void jListCustomersMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jListCustomersMouseClicked

        if (evt.getClickCount() == 2) {
            selectedCustomer = (CustomerInfo) jListCustomers.getSelectedValue();
            dispose();
        }

    }//GEN-LAST:event_jListCustomersMouseClicked

private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed

        m_jtxtTaxID.reset();
        m_jtxtSearchKey.reset();
        m_jtxtName.reset();

        m_jtxtTaxID.activate();

        cleanSearch();
}//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {

        try {
            jListCustomers.setModel(new MyListData(lpr.loadData()));
            if (jListCustomers.getModel() != null && jListCustomers.getModel().getSize() == 1) {
                jListCustomers.setSelectedIndex(0);
            } else if (jListCustomers.getModel() != null && jListCustomers.getModel().getSize() == 0) {
                if (this.m_jtxtTaxID.getText() != null && this.m_jtxtTaxID.getText().length() < 9) {
                    return;
                }
                SaveProvider save = getSaveProvider();
                customerNewAfip = new Object[23];
                customerNewAfip[0] = UUID.randomUUID().toString();
                customerNewAfip[1] = this.m_jtxtTaxID.getText();
                customerNewAfip[2] = this.m_jtxtTaxID.getText();
                customerNewAfip[5] = Boolean.TRUE; //visible
                customerNewAfip[7] = Formats.CURRENCY.parseValue("0", new Double(0.0));
                this.obtenerDatosDelCliente(customerNewAfip);
                if (customerNewAfip[3] != null && customerNewAfip[22] != null) {
                    //guardamos al cliente
                    save.insertData(customerNewAfip);
                    //recuperamos al cliente consultando por el cuit
                    jListCustomers.setModel(new MyListData(lpr.loadData()));
                    if (jListCustomers.getModel() != null && jListCustomers.getModel().getSize() == 1) {
                        jListCustomers.setSelectedIndex(0);
                        JOptionPane.showMessageDialog((Component) evt.getSource(), "Se creo el cliente correctamente", "Informacion",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        } catch (BasicException e) {
            Logger.getLogger(JCustomerFinder.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public Object getDataFromAFIP(String myURL) {
        Logger.getLogger(JCustomerFinder.class.getName()).log(Level.INFO, "Requested URL:{0}", myURL);
        StringBuilder sb = new StringBuilder();
        URLConnection urlConn;
        InputStreamReader in;
        try {
            URL url = new URL(myURL);
            urlConn = url.openConnection();
            if (urlConn != null) {
                urlConn.setReadTimeout(60 * 1000);
            }
            if (urlConn != null && urlConn.getInputStream() != null) {
                in = new InputStreamReader(urlConn.getInputStream(),
                        Charset.defaultCharset());
                BufferedReader bufferedReader = new BufferedReader(in);
                int cp;
                while ((cp = bufferedReader.read()) != -1) {
                    sb.append((char) cp);
                }
                bufferedReader.close();
                in.close();
            }
        } catch (IOException e) {
            if (e instanceof java.net.UnknownHostException) {
                JOptionPane.showMessageDialog(
                        (Component) this,
                        "Error conectividad con Internet",
                        "Informacion",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(
                        (Component) this,
                        "Error al realizar llamada a servicio web. Utilizar la pantalla de creacion de Cliente",
                        "Informacion",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
            Logger.getLogger(JCustomerFinder.class.getName()).log(Level.SEVERE, "Exception while calling URL:" + myURL, e);
        }

        Object afipData = null;
        if (sb.length() > 0) {
            JSONObject afipObject = new JSONObject(sb.toString());
            if ((Boolean) afipObject.get("success") == true) {
                afipData = afipObject.get("data");
            } else {
                String descError = (String) ((JSONObject) afipObject.get("error")).get("mensaje");
                JOptionPane.showMessageDialog(
                        (Component) this,
                        descError,
                        "Mensaje de AFIP",
                        JOptionPane.INFORMATION_MESSAGE
                );
                Logger.getLogger(JCustomerFinder.class.getName()).log(Level.SEVERE, "Mensaje de AFIP:{0}", descError);
            }
        }

        return afipData;
    }

    private void obtenerDatosDelCliente(Object[] customer) {
        JSONObject personaData = (JSONObject) this.getDataFromAFIP(MessageFormat.format("https://soa.afip.gob.ar/sr-padron/v2/persona/{0}", this.m_jtxtTaxID.getText()));
        Iterator iterator;
        String taxCategory = null;

        if (personaData != null) {
            customer[3] = personaData.getString("nombre");
            if (personaData.has("domicilioFiscal")) {
                JSONObject domicilioFiscal = personaData.getJSONObject("domicilioFiscal");
                try {
                    customer[16] = domicilioFiscal.getString("direccion");
                } catch (JSONException e) {
                    Logger.getLogger(JCustomerFinder.class.getName()).log(Level.INFO, e.getMessage(), e);
                }
                try {
                    customer[18] = domicilioFiscal.getString("codPostal");
                } catch (JSONException e) {
                    Logger.getLogger(JCustomerFinder.class.getName()).log(Level.INFO, e.getMessage(), e);
                }
                try {
                    customer[19] = domicilioFiscal.getString("localidad");
                } catch (JSONException e) {
                    Logger.getLogger(JCustomerFinder.class.getName()).log(Level.INFO, e.getMessage(), e);
                }

                Integer idProvincia = domicilioFiscal.getInt("idProvincia");
                String descProvincia = null;
                JSONArray provinciasData = (JSONArray) this.getDataFromAFIP("https://soa.afip.gob.ar/parametros/v1/provincias");
                if (provinciasData != null) {
                    iterator = provinciasData.iterator();
                    while (iterator.hasNext() && descProvincia == null) {
                        JSONObject provinciaObject = (JSONObject) iterator.next();
                        if ((Integer) provinciaObject.get("idProvincia") == idProvincia) {
                            descProvincia = provinciaObject.getString("descProvincia");
                        }
                    }
                }
                customer[20] = descProvincia;
                customer[21] = "ARGENTINA";
            }

            JSONArray impuestosData = null;
            try {
                impuestosData = personaData.getJSONArray("impuestos");
            } catch (JSONException e) {
                Logger.getLogger(JCustomerFinder.class.getName()).log(Level.INFO, e.getMessage(), e);
            }

            if (impuestosData == null) {
                taxCategory = "31fe0ca8-f14d-4eef-b93b-e93a56aeeb48"; // ?? - F:Consumidor Final
                JOptionPane.showMessageDialog(
                        (Component) this,
                        "Se asigna categoría: Consumidor final",
                        "Categoría ante el IVA",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                iterator = impuestosData.iterator();
                while (iterator.hasNext() && taxCategory == null) {
                    Integer codImpuesto = (Integer) iterator.next();
                    if (codImpuesto >= 20 && codImpuesto <= 24) {
                        taxCategory = "9800ba89-b0e4-4fa3-bd2e-11e75e21f98e"; // MONOTRIBUTO - M:Responsable Monotributo
                    } else if (codImpuesto == 30) {
                        taxCategory = "05dd21ed-28c5-46a8-b359-280e76ef77d9"; // IVA - I:Iva Responsable Inscripto
                    } else if (codImpuesto == 32) {
                        taxCategory = "75d0b1ce-e448-43e2-9b73-78ed4cea4214"; // IVA EXENTO - E:Iva Exento
                    } else if (codImpuesto == 33) {
                        taxCategory = "528d77b7-769c-4d6c-918f-ab5636bb9789"; // IVA RESPONSABLE NO INSCRIPTO - R:Iva Responsable No Inscripto
                    }
                }
                if (taxCategory == null) {
                    taxCategory = "82ff1846-e920-4f5a-8cac-4ceee256244f"; // ?? - N:No Responsable
                }
            }
            customer[22] = taxCategory;

            customer[4] = "source:afip_web_service";
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JList jListCustomers;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JButton jcmdCancel;
    private javax.swing.JButton jcmdOK;
    private com.openbravo.editor.JEditorKeys m_jKeys;
    private com.openbravo.editor.JEditorString m_jtxtName;
    private com.openbravo.editor.JEditorString m_jtxtSearchKey;
    private com.openbravo.editor.JEditorString m_jtxtTaxID;
    // End of variables declaration//GEN-END:variables
}
