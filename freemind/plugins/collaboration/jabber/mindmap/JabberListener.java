/*FreeMind - A Program for creating and viewing Mindmaps
 *Copyright (C) 2000-2001  Joerg Mueller <joergmueller@bigfoot.com>
 *See COPYING for Details
 *
 *This program is free software; you can redistribute it and/or
 *modify it under the terms of the GNU General Public License
 *as published by the Free Software Foundation; either version 2
 *of the License, or (at your option) any later version.
 *
 *This program is distributed in the hope that it will be useful,
 *but WITHOUT ANY WARRANTY; without even the implied warranty of
 *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *GNU General Public License for more details.
 *
 *You should have received a copy of the GNU General Public License
 *along with this program; if not, write to the Free Software
 *Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package plugins.collaboration.jabber.mindmap;

import java.util.LinkedList;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.echomine.jabber.Jabber;
import com.echomine.jabber.JabberChatMessage;
import com.echomine.jabber.JabberCode;
import com.echomine.jabber.JabberContext;
import com.echomine.jabber.JabberMessageEvent;
import com.echomine.jabber.JabberMessageException;
import com.echomine.jabber.JabberMessageListener;
import com.echomine.jabber.JabberSession;

import freemind.controller.actions.ActionPair;
import freemind.controller.actions.generated.instance.CompoundAction;
import freemind.controller.actions.generated.instance.XmlAction;
import freemind.main.XMLElement;
import freemind.modes.mindmapmode.MindMapController;

/**
 * @author RReppel
 * 
 *  - Connects to a jabber server. - Establishes a private chat with another
 * user. - Listens to a limited number of FreeMind commands sent by the other
 * user. - Performs the FreeMind actions corresponding to the commands sent.
 *  
 */
public class JabberListener {

    // Logging:
    private static java.util.logging.Logger logger;

    MindMapController controller;

    //A queue ensuring FIFO processing of user commands.
    LinkedList commandQueue;

    JabberSession session;

    public JabberListener(MindMapController c,
            MapSharingController sharingWizardController, String jabberServer,
            int port, String userName, String password) {
        controller = c;
        if (logger == null) {
            logger = controller.getController().getFrame().getLogger(
                    this.getClass().getName());
        }
        commandQueue = new LinkedList();
        JabberContext context = new JabberContext(userName, password,
                jabberServer);
        Jabber jabber = new Jabber();
        session = jabber.createSession(context);
        try {
            session.connect(jabberServer, port);
            session.getUserService().login();
            System.out.println("User logged in.\n");
            session.getPresenceService().setToAvailable("FreeMind Session",
                    null, false);

            //Send a test message:
            //JabberChatService chat = session.getChatService();
            //chat.sendPrivateMessage(new JID("lucy@rreppel-linux"), "FreeMind
            // launched.", false);

            session.addMessageListener(new FreeMindJabberMessageListener(
                    sharingWizardController)); //end addMessageListener
        } //end MindMapJabberController
        catch (Exception ex) {
            ex.printStackTrace();
            String message;
            //TODO: Descriptive error message on Jabber server connection
            // failure.
            if (ex.getClass().getName().compareTo(
                    "com.echomine.jabber.JabberMessageException") == 0) {
                JabberMessageException jabberMessageException = (JabberMessageException) ex;
                message = jabberMessageException.getErrorMessage();

            } else {
                message = ex.getClass().getName() + "\n\n" + ex.getMessage();
            } //endif
            JFrame frame = new JFrame();
            JOptionPane.showMessageDialog(frame, message, "Error",
                    JOptionPane.ERROR_MESSAGE);
            //TODO: Bug: Do not move to the next screen when a connection error
            // has occurred. Do not set status to "connected".
        }
    }

    /**
     * @return
     */
    public JabberSession getSession() {
        return session;
    }

    /**
     * 
     * @author RReppel
     * 
     * Listens to received Jabber messages and initiates the appropriate
     * FreeMind actions.
     *  
     */
    private class FreeMindJabberMessageListener implements
            JabberMessageListener {

        MapSharingController sharingWizardController;

        public FreeMindJabberMessageListener(
                MapSharingController sharingWizardController) {
            super();
            this.sharingWizardController = sharingWizardController;
        }

        public void messageReceived(JabberMessageEvent event) {
            if (event.getMessageType() != JabberCode.MSG_CHAT)
                return;
            JabberChatMessage latestMsg = (JabberChatMessage) event
                    .getMessage();
            if (latestMsg.getType().equals(JabberChatMessage.TYPE_CHAT)
                    || latestMsg.getType()
                            .equals(JabberChatMessage.TYPE_NORMAL)) {
                commandQueue.addLast(latestMsg); //Add the message to the end
                                                 // of the list of commands to
                                                 // be applied.
                logger.info("Queue has " + commandQueue.size() + " items.");

                JabberChatMessage msg = (JabberChatMessage) commandQueue
                        .removeFirst(); //Process the first command in the
                                        // list.
                logger.info("message " + msg + " from "
                        + msg.getFrom().getUsername() + " is reply required:"
                        + msg.isReplyRequired());

                String msgString = msg.getBody();
                //TODO: Should really use a validating parser here...
                if (msgString.startsWith("<fmcmd") && msgString.endsWith("/>")) {
                    XMLElement xml = new XMLElement();
                    xml.parseString(msg.getBody());
                    String cmd = xml.getStringAttribute("cmd");
                    if (cmd != null) {
                        try {
                            if (cmd.compareTo(JabberSender.REQUEST_MAP_SHARING) == 0) {
                                String username = xml
                                        .getStringAttribute("user");
                                sharingWizardController
                                        .setMapSharingRequested(username);
                            } else if (cmd
                                    .compareTo(JabberSender.ACCEPT_MAP_SHARING) == 0) {
                                String username = xml
                                        .getStringAttribute("user");
                                sharingWizardController
                                        .setMapShareRequestAccepted(username,
                                                true);
                            } else if (cmd
                                    .compareTo(JabberSender.DECLINE_MAP_SHARING) == 0) {
                                String username = xml
                                        .getStringAttribute("user");
                                sharingWizardController
                                        .setMapShareRequestAccepted(username,
                                                false);
                            } else if (cmd
                                    .compareTo(JabberSender.STOP_MAP_SHARING) == 0) {
                                String username = xml
                                        .getStringAttribute("user");
                                sharingWizardController
                                        .setSharingStopped(username);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } //end catch
                    } //endif
                    // } //endfor
                } else {
                    CompoundAction pair = (CompoundAction) sharingWizardController
                            .getController().unMarshall(msgString);
                    if (pair
                            .getCompoundActionOrSelectNodeActionOrCutNodeAction()
                            .size() != 2) {
                        //FIXME: Warn the user
                        System.out.println("Cannot process the message "
                                + msgString);
                        return;
                    }
                    XmlAction doAction = (XmlAction) pair
                            .getCompoundActionOrSelectNodeActionOrCutNodeAction()
                            .get(0);
                    XmlAction undoAction = (XmlAction) pair
                            .getCompoundActionOrSelectNodeActionOrCutNodeAction()
                            .get(1);
                    final ActionPair ePair = new ActionPair(doAction,
                            undoAction);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            sharingWizardController.setSendingEnabled(false);
                            try {
                                sharingWizardController.getController()
                                        .getActionFactory()
                                        .executeAction(ePair);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            sharingWizardController.setSendingEnabled(true);
                        }
                    });
                }//endif
            } //endif
        } //end messageReceived
    }

}

