/*
 * Copyright (c) 2006-2007, AIOTrade Computing Co. and Contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *  o Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer. 
 *    
 *  o Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution. 
 *    
 *  o Neither the name of AIOTrade Computing Co. nor the names of 
 *    its contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *    
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.aiotrade.platform.modules.netbeans.ui.actions;

import java.io.IOException;
import java.io.PrintStream;
import javax.swing.JOptionPane;
import org.aiotrade.lib.chartview.persistence.ContentsPersistenceHandler
import org.aiotrade.lib.math.timeseries.datasource.DataContract;
import org.aiotrade.lib.securities.PersistenceManager
import org.aiotrade.lib.securities.dataserver.QuoteContract
import org.aiotrade.platform.modules.netbeans.ui.explorer.SymbolListTopComponent
import org.aiotrade.platform.modules.ui.dialog.ImportSymbolDialog
import org.openide.ErrorManager;
import org.openide.filesystems.FileLock;
import org.openide.loaders.DataFolder;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;

/**
 *
 * @author Caoyuan Deng
 */
class AddSymbolAction extends CallableSystemAction {
    
  def performAction {
    java.awt.EventQueue.invokeLater(new Runnable {
        def run {
          val symbolListTc = SymbolListTopComponent
          symbolListTc.requestActive
                
          val selectedNodes = symbolListTc.getExplorerManager.getSelectedNodes
          var currentNode = if (selectedNodes.length > 0) {
            selectedNodes(0)
          } else null

          var currentFolder = if (currentNode != null) {
            currentNode.getLookup.lookup(classOf[DataFolder])
          } else null
                
          if (currentFolder == null) {
            /** add this stock in root folder */
            currentNode = symbolListTc.getExplorerManager.getRootContext
            currentFolder = currentNode.getLookup.lookup(classOf[DataFolder])
          }
                
          //- expand this node
          symbolListTc.getExplorerManager.setExploredContext(currentNode)
                
          // --- Now begin the dialog
                
          val quoteContract = new QuoteContract
          val pane = new ImportSymbolDialog(WindowManager.getDefault.getMainWindow, quoteContract, true)
          if (pane.showDialog != JOptionPane.OK_OPTION) {
            return
          }
                
          /** quoteContract may bring in more than one symbol, should process it later */
          for (symbol <- quoteContract.symbol.split(",")) {
            val symbol1 = symbol.trim
                    
            /** dataSourceDescriptor may has been set to more than one symbols, process it here */
            quoteContract.symbol = symbol1
                    
            createSymbolXmlFile(currentFolder, symbol1, quoteContract)
          }
        }
      })
        
  }
    
  private def createSymbolXmlFile(folder: DataFolder, symbol: String, quoteContract: QuoteContract) {
        
    val folderObject = folder.getPrimaryFile
    val baseName = symbol
    var ix = 1
    while (folderObject.getFileObject(baseName + ix, "xml") != null) {
      ix += 1
    }
        
    var lock: FileLock = null
    try {
      val writeTo = folderObject.createData(baseName + ix, "xml")
      lock = writeTo.lock
      val out = new PrintStream(writeTo.getOutputStream(lock))
            
      val contents = PersistenceManager().defaultContents
      /** clear default dataSourceContract */
      contents.clearDescriptors(classOf[DataContract[_]])
            
      contents.uniSymbol = symbol
      contents.addDescriptor(quoteContract)
            
      out.print(ContentsPersistenceHandler.dumpContents(contents))
            
      /** should remember to do out.close() here */
      out.close
            
      /**
       * set attr: "new" for opening the view when a new node is
       * created late by SymbolNode.SymbolFolderChildren.creatNodes()
       */
      writeTo.setAttribute("new", true)
    } catch {case ex: IOException => ErrorManager.getDefault.notify(ex)
    } finally {
      if (lock != null) {
        lock.releaseLock
      }
    }
        
  }
    
  def getName = {
    "Add Symbol"
  }
    
    
  def getHelpCtx: HelpCtx = {
    HelpCtx.DEFAULT_HELP
  }
    
  override protected def iconResource: String = {
    "org/aiotrade/platform/modules/netbeans/ui/resources/newSymbol.gif"
  }
    
  override protected def asynchronous: Boolean = {
    false;
  }
    
    
}


