/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.openbravo.pos.printer.fiscal;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;
import ifepson.IFException;
import ifepson.IFReturnValue;
import ifepson.ifCommand;
import ifepson.commands.pagoCancelDescRecaFNC;
import ifepson.commands.pagoCancelDescRecaTique.Calificador;
import ifepson.commands.solEstado;
import ifepson.doc.DataType;
import ifepson.doc.IndexedOut;
import ifepson.doc.Parametro;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import myjob.func.general.GeneralFunc;
import myjob.func.io.PortConfig;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 *
 * @author wnasich
 */
public class FiscalEpson {

    protected int timeOut = 1000;
    protected PortConfig portConfig;
    protected List<ifCommand> commandsToSend = new ArrayList<ifCommand>();
    protected Map<String, Class> ifEpsonCommands = new HashMap<String, Class>();
    protected Map<IndexedOut, String> respuesta = new EnumMap<IndexedOut, String>(IndexedOut.class);
    protected byte secuencia = 0x20;
    protected String batchOriginal = "";
    protected boolean noClosePortBetweenCommands = true;
    protected String separador = "\\|";
    protected int largoDesc = 26;
    protected boolean isRxTxInitiated = false;

    public String getBatchOriginal() {
        return batchOriginal;
    }

    public void setBatchOriginal(String batchOriginal) {
        this.batchOriginal = batchOriginal;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(int timeOut) {
        this.timeOut = timeOut;
    }

    public PortConfig getPortConfig() {
        return portConfig;
    }

    public void setPortConfig(PortConfig config) {
        this.portConfig = config;
    }

    public boolean isNoClosePortBetweenCommands() {
        return noClosePortBetweenCommands;
    }

    public void setNoClosePortBetweenCommands(boolean noClosePortBetweenCommands) {
        this.noClosePortBetweenCommands = noClosePortBetweenCommands;
    }

    public FiscalEpson(String portName) {
        portConfig = new PortConfig();
        portConfig.setPortName(portName);
        portConfig.setDataBits(8);

        loadCommands();
        resolvePort();

        Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Secuencia seteada en " + secuencia);
    }

    public void InitRxTx(String nativeLibPath) {
        if (isRxTxInitiated) {
            return;
        }

        Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "utilizando driver ubicado en " + nativeLibPath);
        try {
            myjob.func.classutils.ClassFunc.addLibPathDir(nativeLibPath);
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        isRxTxInitiated = true;

    }

    /**
     * Parsea cada line en un comando individual para generar el Batch
     * Además carga cada linea en un texto batchOriginal para usos en log
     * @param lines
     * @param timeOut
     * @param config
     * @return
     */
    public void fromLines(ArrayList<String> lines) {
        ifCommand comm;

        for (String linea : lines) {
            if (linea.trim().length() > 0) {
                batchOriginal += linea + "\n";
                comm = parseCommand(linea.trim());
                if (comm == null) {
                    Logger.getLogger(FiscalEpson.class.getName()).log(Level.ERROR, "No se pudo generar comando de " + linea);
                } else {
                    addCommand(comm);
                }
            }
        }
    }

    /**
     * Devuelve un mapa con las respuestas indexadas y su valor
     * @return
     */
    public Map<IndexedOut, String> getRespuesta() {
        return respuesta;
    }

    /**
     * Establece la respuesta
     * @param respuesta
     */
    public void setRespuesta(Map<IndexedOut, String> respuesta) {
        this.respuesta = respuesta;
    }

    /**
     * Devuelve una lista con los commandsToSend
     * @return
     */
    public List<ifCommand> getComandsToSend() {
        return commandsToSend;
    }

    /**
     * Establece la lista de commandsToSend
     * @param commandsToSend
     */
    public void setComandsToSend(List<ifCommand> comandos) {
        this.commandsToSend = comandos;
    }

    public void reset() {
        this.commandsToSend.clear();
        this.batchOriginal = "";
    }

    /**
     * Agrega un comando a la lista de commandsToSend
     * @param comando
     */
    public void addCommand(ifCommand comando) {
        commandsToSend.add(comando);
    }

    /**
     * Envía los commandsToSend en orden al controlador fiscal
     */
    public void run() {

        IFReturnValue retComm = IFReturnValue.OK;

        SerialPort port = null;

        if (this.noClosePortBetweenCommands) {
            try {
                Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Abriendo el puerto " + this.getPortConfig().getPortName());

                port = (SerialPort) CommPortIdentifier.getPortIdentifier(this.getPortConfig().getPortName()).open("FiscalEpson", 2000);

                port.setSerialPortParams(
                        this.getPortConfig().getBaudRate(),
                        this.getPortConfig().getDataBits(),
                        this.getPortConfig().getStopBits(),
                        this.getPortConfig().getParity());

                port.setDTR(true);
            } catch (UnsupportedCommOperationException ex) {
                java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, "Operacion del puerto no soportada", ex);
                this.respuesta.put(IndexedOut.PUERTO_SERIE, "Operacion del puerto no soportada");
                return;
            } catch (PortInUseException ex) {
                java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, "Puerto en uso", ex);
                this.respuesta.put(IndexedOut.PUERTO_SERIE, "Puerto en uso");
                return;
            } catch (NoSuchPortException ex) {
                java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, "El puerto no existe", ex);
                this.respuesta.put(IndexedOut.PUERTO_SERIE, "El puerto no existe");
                return;
            }

        }

        for (ifCommand command : commandsToSend) {
            try {

                retComm = IFReturnValue.OK;

                Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Enviando comando " + command.getNombreA() + "  nro de serie:  " + secuencia);

                if (port == null) {
                    retComm = command.ejecutar(this.getPortConfig(), getSerial());
                } else {
                    retComm = command.ejecutar(port, getSerial());
                }

            } catch (IFException ex) {
                retComm = IFReturnValue.UNKNOW_ERROR;
                this.respuesta.put(IndexedOut.OTROS_ERRORES, ex.getMessage());
                java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, ex.getMessage(), ex);
            } catch (NoSuchPortException ex) {
                retComm = IFReturnValue.UNKNOW_SERIAL_PORT_ERROR;
                this.respuesta.put(IndexedOut.PUERTO_SERIE, "No existe el puerto serie");
                Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, "No existe el puerto serie", ex);
            } catch (PortInUseException ex) {
                retComm = IFReturnValue.SERIAL_PORT_IN_USE_ERROR;
                this.respuesta.put(IndexedOut.PUERTO_SERIE, "Puerto serie en uso");
                Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, "Puerto serie en uso", ex);
            } catch (UnsupportedCommOperationException ex) {
                retComm = IFReturnValue.UNKNOW_SERIAL_PORT_ERROR;
                this.respuesta.put(IndexedOut.PUERTO_SERIE, "Operacion de puerto serie no soportada");
                Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, "Operacion del puerto serie no soportada", ex);
            }

            Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Combinando las respuestas de los comandos individuales");

            CombinarRespuesta(command.getRespuesta());

            if (retComm != IFReturnValue.OK) {

                String tmpStr = "Error ejecutando " + command.getNombreA();

                tmpStr += " --" + retComm.getDescription() + "--";

                tmpStr += " con los siguientes parametros\n";

                for (Parametro p : command.getParams().keySet()) {
                    tmpStr += p.getCodigo().toString() + "(" + p.toString() + ") = " + command.getParams().get(p) + "\n";
                }

                tmpStr += " las respuestas combinadas son\n";

                for (IndexedOut io : respuesta.keySet()) {
                    tmpStr += io.name() + " " + io.getDescripcion() + " = " + respuesta.get(io) + "\n";
                }

                Logger.getLogger(FiscalEpson.class.getName()).log(Level.ERROR, tmpStr + "\n" + this.toString());

                break;
            }
        }

        Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Fin de comandos FiscalEpson con retComm " + retComm);

        if (retComm != IFReturnValue.OK && this.commandsToSend.size() > 0 && this.respuesta.containsKey(IndexedOut.EF__DOCUM_FISC_ABIERTO)) {

            Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "rutina de salvado de errores en FiscalEpson");

            retComm = IFReturnValue.OK;

            ifCommand comm = null;
            try {
                // un error en la ejecucion
                // trato de cancelar el tique en caso que se pueda
                if (commandsToSend.get(0).getNombreA().equals("TIQUEABRE")) {

                    Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Intentando cancelar comprobante tiquet");

                    comm = new ifepson.commands.pagoCancelDescRecaTique();
                    ((ifepson.commands.pagoCancelDescRecaTique) comm).setCalificador(Calificador.CANCELAR_COMPROBANTE);
                    if (port == null) {
                        retComm = comm.ejecutar(this.getPortConfig(), getSerial());
                    } else {
                        retComm = comm.ejecutar(port, getSerial());
                    }
                }

                if (commandsToSend.get(0).getNombreA().equals("FACTABRE")) {

                    Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Intentando cancelar comprobante factura / nota credito");

                    comm = new ifepson.commands.pagoCancelDescRecaFNC();
                    ((ifepson.commands.pagoCancelDescRecaFNC) comm).setCalificador(pagoCancelDescRecaFNC.Calificador.CANCELAR_COMPROBANTE);
                    if (port == null) {
                        retComm = comm.ejecutar(portConfig, getSerial());
                    } else {
                        retComm = comm.ejecutar(port, getSerial());
                    }
                }
            } catch (IFException ex) {
                retComm = IFReturnValue.SERIAL_PORT_UNSOPORTED_OP_ERROR;
                this.respuesta.put(IndexedOut.OTROS_ERRORES, ex.getMessage());
                java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, ex.getMessage(), ex);
            } catch (NoSuchPortException ex) {
                retComm = IFReturnValue.UNKNOW_SERIAL_PORT_ERROR;
                this.respuesta.put(IndexedOut.PUERTO_SERIE, "No existe el puerto serie");
                Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, "No existe el puerto serie", ex);
            } catch (PortInUseException ex) {
                retComm = IFReturnValue.SERIAL_PORT_IN_USE_ERROR;
                this.respuesta.put(IndexedOut.PUERTO_SERIE, "Puerto serie en uso");
                Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, "Puerto serie en uso", ex);
            } catch (UnsupportedCommOperationException ex) {
                retComm = IFReturnValue.SERIAL_PORT_UNSOPORTED_OP_ERROR;
                this.respuesta.put(IndexedOut.PUERTO_SERIE, "Operacion de puerto serie no soportada");
                Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, "Operacion del puerto serie no soportada", ex);
            }

            CombinarRespuesta(comm.getRespuesta());

            if (retComm != IFReturnValue.OK) {
                Logger.getLogger(FiscalEpson.class.getName()).log(Level.ERROR, "NO SE PUDO SALVAR EL ERROR ");
            }

        }

        ifCommand fin = new solEstado();
        if (this.secuencia != 127) {
            fin.setSecuencia((byte) 127);
        } else {
            fin.setSecuencia((byte) 100);
        }

        try {
            Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Enviando comando con serie " + fin.getSecuencia() + " para que sincronice");
            if (port == null) {
                retComm = fin.ejecutar(this.getPortConfig(), getSerial());
            } else {
                retComm = fin.ejecutar(port, getSerial());
            }
            Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "fin volvio con " + retComm);
        } catch (NoSuchPortException ex) {
            java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, "Error al ejecutar comando final, no existe el puerto serie", ex);
        } catch (PortInUseException ex) {
            java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, "Error al ejecutar comando final, puerto serie en uso", ex);
        } catch (UnsupportedCommOperationException ex) {
            java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, "Error al ejecutar comando final, comando no soportado el puerto serie", ex);
        } catch (IFException ex) {
            java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, "Error al ejecutar comando final, IFException", ex);
        }

        if (port != null) {
            Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "\nCerrando Puerto\n");
            port.close();
        }

    }

    /**
     * Combina la respuesta resp con la que tiene
     * @param resp
     */
    private void CombinarRespuesta(Map<IndexedOut, String> resp) {
        for (IndexedOut key : resp.keySet()) {
            respuesta.put(key, resp.get(key));
        }
    }

    /**
     * devuelve el serial incrementado
     * @return
     */
    public byte getSerial() {
        secuencia++;

        if (secuencia < 0x20) {
            secuencia = 0x20;
        }
        if (secuencia >= 0x7F) {
            secuencia = 0x20;
        }
        return secuencia;
    }

    /**
     * Transforma una linea de texto en un comando fiscal
     * @param linea
     * @return
     */
    public ifCommand parseCommand(String linea) {

        String[] parser = linea.split(separador);

        Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Separando comando " + linea + " con separador " + separador + " y queda como comando:" + parser[0]);

        Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Seteando largo maximo de descripcion para Tique y TiqueFact en " + largoDesc);

        ifepson.doc.Parametro.IIT__DESCRIPCION_PROD.setLargo(largoDesc);
        ifepson.doc.Parametro.IIFNC__DESCRIPCION_PROD.setLargo(largoDesc);

        ifCommand comm = null;

        for (String commName : ifEpsonCommands.keySet()) {

            if (("@" + commName).toUpperCase().equals(parser[0].toUpperCase())) {
                try {
                    comm = (ifCommand) myjob.func.classutils.ClassFunc.getInstanceFromClassName(ifEpsonCommands.get(commName).getName());
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, "Clase no encontrada", ex);
                } catch (InstantiationException ex) {
                    Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, "No se pudo crear instancia de clase", ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, "Acceso ilegal", ex);
                }
                break;
            }
        }

        if (comm != null) {

            Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "Agregando comando " + comm.getNombreA());

            for (int pos = 1; pos <= comm.getPosiblesParams().size() && pos < parser.length; pos++) {
                if (parser[pos].length() > 0) {
                    String paramVal = parser[pos];

                    Parametro currentParam = comm.getIndexedParam(pos - 1);

                    if (paramVal.length() > currentParam.getLargo()) {
                        paramVal = paramVal.substring(0, paramVal.length());
                    }

                    Method paramSetter = currentParam.getParamSetter();
                    if(paramSetter != null) {
                        try {
                            if (currentParam.getType() == DataType.Integer) {
                                int paramValInt = new Integer(paramVal);
                                    paramSetter.invoke(comm, paramValInt);
                            } else if (currentParam.getType() == DataType.Num2Dec ||
                                    currentParam.getType() == DataType.Num3Dec ||
                                    currentParam.getType() == DataType.Num4Dec ||
                                    currentParam.getType() == DataType.Num8Dec ) {
                                double paramValDouble = new Double(paramVal);
                                paramSetter.invoke(comm, paramValDouble);
                            } else {
                                paramSetter.invoke(comm, paramVal);
                            }
                        } catch (IllegalAccessException ex) {
                            java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                        } catch (IllegalArgumentException ex) {
                            java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                        } catch (InvocationTargetException ex) {
                            java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                        }
                    } else {
                        comm.setParam(pos - 1, paramVal);
                    }
                }
            }
        }

        return comm;
    }

    public void loadCommands() {

        // Class[] commands = myjob.func.classutils.ClassFunc.getClasseInPackage("lib/ifepson.jar", "ifepson.commands");
        String jarPathToFile = ifCommand.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        String decodedPath = null;
        try {
            decodedPath = URLDecoder.decode(jarPathToFile, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            java.util.logging.Logger.getLogger(FiscalEpson.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        // decodedPath = "/home/wnasich/dev/ifepson/dist/ifepson.jar";
        Class[] commands = myjob.func.classutils.ClassFunc.getClasseInPackage(decodedPath, "ifepson.commands");

        Object obj = null;
        ifCommand com = null;

        for (Class c : commands) {
            if (!c.getName().contains("$") && !c.getName().equals("ifepson.commands.ifCommand")) {
                try {
                    obj = myjob.func.classutils.ClassFunc.getInstanceFromClassName(c.getName());
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, null, ex);
                } catch (InstantiationException ex) {
                    Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, null, ex);
                } catch (IllegalAccessException ex) {
                    Logger.getLogger(FiscalEpson.class.getName()).log(Level.FATAL, null, ex);
                }
                if (obj != null) {
                    com = (ifCommand) obj;
                    ifEpsonCommands.put(com.getNombreA(), c);
                }
            }
        }
    }

    public void resolvePort() {

        Logger.getLogger(FiscalEpson.class.getName()).log(Level.DEBUG, "El sistema operativo detectado es:" + GeneralFunc.getOS() + (GeneralFunc.is64OS() ? " 64bit" : " 32bit"));

        String pathToAdd = "lib/rxtx/";

        if (GeneralFunc.getOS().toLowerCase().contains("win")) {
            if (GeneralFunc.is64OS()) {
                pathToAdd += "win64";
            } else {
                pathToAdd += "win32";
            }
        } else {
            if (GeneralFunc.is64OS()) {
                pathToAdd += "linux_x86_64";
            } else {
                pathToAdd += "linux_i686";
            }
        }

        InitRxTx(pathToAdd);
    }

    @Override
    public String toString() {
        String retVal = "FiscalEpson{" + "timeOut=" + timeOut + ", portConfig=" + portConfig + ", serial=" + secuencia + "\n";

        retVal += "\tORIGINAL:\n" + batchOriginal + "\tFIN ORIGINAL\n";

        for (int i = 0; i < commandsToSend.size(); i++) {
            retVal += "\t[" + i + "]" + commandsToSend.get(i).toString().replace("\n", "\n\t") + "\n";
        }

        return retVal + "}";
    }
}
