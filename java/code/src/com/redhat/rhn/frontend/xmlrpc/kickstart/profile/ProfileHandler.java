/**
 * Copyright (c) 2008 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 * 
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation. 
 */
package com.redhat.rhn.frontend.xmlrpc.kickstart.profile;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.SortedSet;
import java.util.Iterator;

import com.redhat.rhn.FaultException;
import com.redhat.rhn.common.util.MD5Crypt;
import com.redhat.rhn.common.hibernate.HibernateFactory;
import com.redhat.rhn.common.localization.LocalizationService;
import com.redhat.rhn.common.security.PermissionException;
import com.redhat.rhn.common.validator.ValidatorError;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.kickstart.KickstartCommand;
import com.redhat.rhn.domain.kickstart.KickstartData;
import com.redhat.rhn.domain.kickstart.KickstartDefaults;
import com.redhat.rhn.domain.kickstart.KickstartFactory;
import com.redhat.rhn.domain.kickstart.KickstartCommandName;
import com.redhat.rhn.domain.kickstart.KickstartIpRange;
import com.redhat.rhn.domain.kickstart.KickstartScript;
import com.redhat.rhn.domain.kickstart.KickstartableTree;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.xmlrpc.BaseHandler;
import com.redhat.rhn.frontend.xmlrpc.InvalidChannelLabelException;
import com.redhat.rhn.frontend.xmlrpc.InvalidKickstartScriptException;
import com.redhat.rhn.frontend.xmlrpc.InvalidScriptTypeException;
import com.redhat.rhn.frontend.xmlrpc.IpRangeConflictException;
import com.redhat.rhn.frontend.xmlrpc.PermissionCheckFailureException;
import com.redhat.rhn.manager.channel.ChannelManager;
import com.redhat.rhn.manager.kickstart.IpAddress;
import com.redhat.rhn.manager.kickstart.KickstartEditCommand;
import com.redhat.rhn.manager.kickstart.KickstartFormatter;
import com.redhat.rhn.manager.kickstart.KickstartIpCommand;
import com.redhat.rhn.manager.kickstart.KickstartOptionsCommand;
import com.redhat.rhn.manager.kickstart.KickstartPartitionCommand;
import com.redhat.rhn.domain.token.ActivationKey;
import com.redhat.rhn.domain.org.Org;
import com.redhat.rhn.domain.rhnpackage.PackageName;
import com.redhat.rhn.domain.role.RoleFactory;
import com.redhat.rhn.frontend.dto.kickstart.KickstartOptionValue;
import com.redhat.rhn.frontend.xmlrpc.kickstart.profile.keys.KeysHandler;

import com.redhat.rhn.frontend.action.kickstart.KickstartHelper;
import com.redhat.rhn.frontend.action.kickstart.KickstartIpRangeFilter;
import com.redhat.rhn.frontend.xmlrpc.kickstart.NoSuchKickstartTreeException;
import com.redhat.rhn.frontend.xmlrpc.kickstart.XmlRpcKickstartHelper;

/**
 * ProfileHandler
 * @version $Rev$
 * @xmlrpc.namespace kickstart.profile
 * @xmlrpc.doc Provides methods to access and modify many aspects of 
 * a kickstart profile.
 */
public class ProfileHandler extends BaseHandler {
    
    /**
     * Set the kickstart tree for a kickstart profile.
     * @param sessionKey User's session key.
     * @param kslabel label of the kickstart profile to be changed.
     * @param kstreeLabel label of the new kickstart tree.
     * @return 1 if successful, exception otherwise.
     * 
     * @xmlrpc.doc Set the kickstart tree for a kickstart profile.
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param_desc("string", "kslabel", "Label of kickstart
     * profile to be changed.")
     * @xmlrpc.param #param_desc("string", "kstreeLabel", "Label of new
     * kickstart tree.")
     * @xmlrpc.returntype #return_int_success()
     */
    public int setKickstartTree(String sessionKey, String kslabel,
            String kstreeLabel) {

        User loggedInUser = getLoggedInUser(sessionKey);
        KickstartData ksdata = KickstartFactory
                .lookupKickstartDataByLabelAndOrgId(kslabel, loggedInUser
                        .getOrg().getId());
        if (ksdata == null) {
            throw new FaultException(-3, "kickstartProfileNotFound",
                    "No Kickstart Profile found with label: " + kslabel);
        }

        KickstartableTree tree = KickstartFactory.lookupTreeByLabel(
                kstreeLabel, loggedInUser.getOrg());
        if (tree == null) {
            throw new NoSuchKickstartTreeException(kstreeLabel);
        }

        KickstartDefaults ksdefault = ksdata.getKsdefault();
        ksdefault.setKstree(tree);
        return 1;
    }

    
    /** 
     * Set the child channels for a kickstart profile.   
     * @param sessionKey User's session key. 
     * @param kslabel label of the kickstart profile to be updated.
     * @param channelLabels labels of the child channels to be set in the 
     * kickstart profile. 
     * @return 1 if successful, exception otherwise.
     *
     * @xmlrpc.doc Set the child channels for a kickstart profile. 
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param_desc("string", "kslabel", "Label of kickstart
     * profile to be changed.")     
     * @xmlrpc.param #param_desc("string[]", "channelLabels", 
     * "List of labels of child channels")
     * @xmlrpc.returntype #return_int_success()
     */    
    public int setChildChannels(String sessionKey, String kslabel, 
            List<String> channelLabels) {

        User loggedInUser = getLoggedInUser(sessionKey);
        KickstartData ksdata = KickstartFactory.
              lookupKickstartDataByLabelAndOrgId(kslabel, loggedInUser.getOrg().getId());
        if (ksdata == null) {
            throw new FaultException(-3, "kickstartProfileNotFound",
                "No Kickstart Profile found with label: " + kslabel);
        }
               
        Long ksid = ksdata.getId();
        KickstartEditCommand ksEditCmd = new KickstartEditCommand(ksid, loggedInUser);
        List<String> channelIds = new ArrayList<String>(); 
        
        for (int i = 0; i < channelIds.size(); i++) {
            Channel channel = ChannelManager.lookupByLabelAndUser(channelLabels.get(i), 
                 loggedInUser);
            if (channel == null) {
                throw new InvalidChannelLabelException();
            }
            String channelId = channel.getId().toString();
            channelIds.add(channelId);
        }
        

        String[] childChannels = new String [channelIds.size()];
        childChannels = (String[]) channelIds.toArray(new String[0]);
        ksEditCmd.updateChildChannels(childChannels);        
        
        return 1;
    }

    /**
     * List the pre and post scripts for a kickstart profile.
     * @param sessionKey key
     * @param label the kickstart label
     * @return list of kickstartScript objects
     * 
     * @xmlrpc.doc List the pre and post scripts for a kickstart profile.
     * profile
     * @xmlprc.param
     * @xmlrpc.param
     * @xmlrpc.returntype #array() $KickstartScriptSerializer #array_end()
     */
    public List<KickstartScript> listScripts(String sessionKey, String label) {
        User loggedInUser = getLoggedInUser(sessionKey);
        checkKickstartPerms(loggedInUser);
        KickstartData data = lookupKsData(label, loggedInUser.getOrg());

        return new ArrayList<KickstartScript>(data.getScripts());

    }

    /**
     * Add a script to a kickstart profile
     * @param sessionKey key
     * @param ksLabel the kickstart label
     * @param contents the contents
     * @param interpreter the script interpreter to use
     * @param type "pre" or "post"
     * @param chroot true if you want it to be chrooted
     * @return the id of the created script
     * 
     * @xmlrpc.doc Add a pre/post script to a kickstart profile.
     * @xmlprc.param #session_key()
     * @xmlrpc.param #param_desc("string", "ksLabel", "The kickstart label to
     * add the script to.")
     * @xmlrpc.param #param_desc("string", "contents", "The full script to
     * add.")
     * @xmlrpc.param #param_desc("string", "interpreter", "The path to the
     * interpreter to use (i.e. /bin/bash). An empty string will use the
     * kickstart default interpreter.")
     * @xmlrpc.param #param_desc("string", "type", "The type of script (either
     * 'pre' or 'post').")
     * @xmlrpc.param #param_desc("boolean", "chroot", "Whether to run the script
     * in the chrooted install location (recommended) or not.")
     * @xmlrpc.returntype int id - the id of the added script
     * 
     */
    public int addScript(String sessionKey, String ksLabel, String contents,
            String interpreter, String type, boolean chroot) {
        User loggedInUser = getLoggedInUser(sessionKey);
        checkKickstartPerms(loggedInUser);
        KickstartData ksData = lookupKsData(ksLabel, loggedInUser.getOrg());

        if (!type.equals("pre") && !type.equals("post")) {
            throw new InvalidScriptTypeException();
        }

        KickstartScript script = new KickstartScript();
        script.setData(contents.getBytes());
        script.setInterpreter(interpreter.equals("") ? null : interpreter);
        script.setScriptType(type);
        script.setChroot(chroot ? "Y" : "N");
        script.setKsdata(ksData);
        ksData.addScript(script);
        HibernateFactory.getSession().save(script);
        return script.getId().intValue();
    }

    /**
     * Remove a script from a kickstart profile.
     * @param sessionKey key
     * @param ksLabel the kickstart to remove a script from
     * @param id the id of the kickstart
     * @return 1 on success
     * 
     * @xmlrpc.doc Remove a script from a kickstart profile.
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #prop_desc("string", "ksLabel", "The kickstart from which
     * to remove the script from.")
     * @xmlrpc.param #prop_desc("int", "scriptId", "The id of the script to
     * remove.")
     * @xmlrpc.returntype #return_int_success()
     * 
     */
    public int removeScript(String sessionKey, String ksLabel, Integer id) {
        User loggedInUser = getLoggedInUser(sessionKey);
        checkKickstartPerms(loggedInUser);
        KickstartData ksData = lookupKsData(ksLabel, loggedInUser.getOrg());

        KickstartScript script = KickstartFactory.lookupKickstartScript(
                loggedInUser.getOrg(), id);
        if (script == null || 
                !script.getKsdata().getLabel().equals(ksData.getLabel())) {
            throw new InvalidKickstartScriptException();
        }

        script.setKsdata(null);
        ksData.getScripts().remove(script);
        KickstartFactory.removeKickstartScript(script);

        return 1;
    }

    /**
     * returns the fully formatted kickstart file
     * @param sessionKey key
     * @param ksLabel the label to download
     * @param host The host/ip to use when referring to the server itself
     * @return the kickstart file
     * 
     * @xmlrpc.doc Download the full contents of a kickstart file.
     * @xmlrpc.param #param_desc("string", "ksLabel", "The label of the
     * kickstart to download.")
     * @xmlrpc.param #param_desc("string", "host", "The host to use when
     * referring to the satellite itself (Usually this should be the FQDN of the
     * satellite, but could be the ip address or shortname of it as well.")
     * @xmlrpc.returntype string - The contents of the kickstart file. Note: if
     * an activation key is not associated with the kickstart file, registration
     * will not occur in the satellite generated %post section. If one is
     * associated, it will be used for registration.
     * 
     * 
     */
    public String downloadKickstart(String sessionKey, String ksLabel,
            String host) {
        User loggedInUser = getLoggedInUser(sessionKey);
        KickstartData ksData = lookupKsData(ksLabel, loggedInUser.getOrg());
        KickstartFormatter form = new KickstartFormatter(host, ksData);
        return form.getFileData();
    }

    /**
     * Set the partitioning scheme for a kickstart profile.
     * @param sessionKey An active session key.
     * @param ksLabel A kickstart profile label.
     * @param scheme The partitioning scheme.
     * @return 1 on success
     * @throws FaultException
     * @xmlrpc.doc Set the partitioning scheme for a kickstart profile.
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param_desc("string", "ksLabel", "The label of the
     * kickstart profile to update.")
     * @xmlrpc.param #param_desc("string[]", "scheme", "The partitioning scheme
     * is a list of partitioning command strings used to setup the partitions,
     * volume groups and logical volumes.")
     * @xmlrpc.returntype #return_int_success()
     */
    public int setPartitioningScheme(String sessionKey, String ksLabel,
            List<String> scheme) {
        User user = getLoggedInUser(sessionKey);
        KickstartData ksdata = lookupKsData(ksLabel, user.getOrg());
        Long ksid = ksdata.getId();
        KickstartPartitionCommand command = new KickstartPartitionCommand(ksid,
                user);
        StringBuilder sb = new StringBuilder();
        for (String s : scheme) {
            sb.append(s);
            sb.append('\n');
        }
        ValidatorError err = command.parsePartitions(sb.toString());
        if (err != null) {
            throw new FaultException(-4, "PartitioningSchemeInvalid", err
                    .toString());
        }
        command.store();
        return 1;
    }

    /**
     * Get the partitioning scheme for a kickstart profile.
     * @param sessionKey An active session key
     * @param ksLabel A kickstart profile label
     * @return The profile's partitioning scheme. This is a list of commands
     * used to setup the partitions, logical volumes and volume groups.
     * @throws FaultException
     * @xmlrpc.doc Get the partitioning scheme for a kickstart profile.
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param_desc("string", "ksLabel", "The label of a kickstart
     * profile.")
     * @xmlrpc.returntype string[] - A list of partitioning commands used to
     * setup the partitions, logical volumes and volume groups."
     */
    @SuppressWarnings("unchecked")
    public List<String> getPartitioningScheme(String sessionKey, String ksLabel) {
        User user = getLoggedInUser(sessionKey);
        KickstartData ksdata = lookupKsData(ksLabel, user.getOrg());
        List<String> list = new ArrayList<String>();
        for (KickstartCommand cmd : (List<KickstartCommand>) ksdata
                .getPartitions()) {
            String s = "partition " + cmd.getArguments();
            list.add(s);
        }
        for (KickstartCommand cmd : (Set<KickstartCommand>) ksdata
                .getVolgroups()) {
            String s = "volgroup " + cmd.getArguments();
            list.add(s);
        }
        for (KickstartCommand cmd : (Set<KickstartCommand>) ksdata.getLogvols()) {
            String s = "logvol " + cmd.getArguments();
            list.add(s);
        }
        return list;
    }

    /** 
     * Get advanced options for existing kickstart profile.
     * @param sessionKey User's session key. 
     * @param ksLabel label of the kickstart profile to be updated.
     * @return An array of advanced options
     * @throws FaultException A FaultException is thrown if
     *         the profile associated with ksLabel cannot be found 
     *
     * @xmlrpc.doc Get advanced options for a kickstart profile. 
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param_desc("string", "ksLabel", "Label of kickstart
     * profile to be changed.")
     * @xmlrpc.returntype 
     * #array()
     * $KickstartAdvancedOptionsSerializer
     * #array_end()
     */
    
    public Object[] getAdvancedOptions(String sessionKey, String ksLabel)
    throws FaultException {
        User loggedInUser = getLoggedInUser(sessionKey);
        KickstartData ksdata = KickstartFactory.
            lookupKickstartDataByLabelAndOrgId(ksLabel, loggedInUser.
                    getOrg().getId());
        if (ksdata == null) {
            throw new FaultException(-3, "kickstartProfileNotFound",
                    "No Kickstart Profile found with label: " + ksLabel);
        }       
        
        Set<KickstartCommand> options = ksdata.getOptions();
        return options.toArray();
    }

    /**
     * Set advanced options in a kickstart profile
     * @param sessionKey the session key
     * @param ksLabel the kickstart label
     * @param options the advanced options to set
     * @return 1 if success, exception otherwise
     * @throws FaultException A FaultException is thrown if
     *         the profile associated with ksLabel cannot be found
     *         or invalid advanced option is provided
     *             
     * @xmlrpc.doc Set advanced options for a kickstart profile.
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param("string","ksLabel")
     * @xmlrpc.param 
     *      #struct("advanced options")
     *          #prop_desc("string", "name", "Name of the advanced option")
     *          #prop_desc("string", "arguments", "Arguments of the option")
     *      #struct_end()  
     * @xmlrpc.returntype #return_int_success()
     */
    public int setAdvancedOptions(String sessionKey, String ksLabel, List<Map> options) 
    throws FaultException {
        User user = getLoggedInUser(sessionKey);
        KickstartData ksdata = KickstartFactory.
            lookupKickstartDataByLabelAndOrgId(ksLabel, user.
                    getOrg().getId());        
        if (ksdata == null) {
            throw new FaultException(-3, "kickstartProfileNotFound", 
            "No Kickstart Profile found with label: " + ksLabel);
        }
        
        String[] validOptionNames = new String[] {"autostep", "interactive", "install", 
                "upgrade", "text", "network", "cdrom", "harddrive", "nfs", "url", 
                "lang", "langsupport", "keyboard", "mouse", "device", "deviceprobe", 
                "zerombr", "clearpart", "bootloader", "timezone", "auth", "rootpw", 
                "selinux", "reboot", "firewall", "xconfig", "skipx", "key", 
                "ignoredisk", "autopart", "cmdline", "firstboot", "graphical", "iscsi", 
                "iscsiname", "logging", "monitor", "multipath", "poweroff", "halt", 
                "service", "shutdown", "user", "vnc", "zfcp"};
        
        Set<String> validOptions = new HashSet<String>();
        for (int i = 0; i < validOptionNames.length; i++) {
            validOptions.add(validOptionNames[i]);
        }
        
        Set<String> givenOptions = new HashSet<String>();
        for (Map option : options) {
            String name = (String) option.get("name");
            givenOptions.add(name);
            if (!validOptions.contains(name)) {
                throw new FaultException(-5, "invalidKickstartCommandName", 
                        "Invalid kickstart command Option: " + name);
            }                
        }
         
        Long ksid = ksdata.getId();
        KickstartHelper helper = new KickstartHelper(null);
        KickstartOptionsCommand cmd = new KickstartOptionsCommand(ksid, user, helper);
        
        List<KickstartCommandName> requiredOptions = KickstartFactory.
            lookupKickstartRequiredOptions();
        
        List<String> requiredOptionNames = new ArrayList<String>();
        for (int i = 0; i < requiredOptions.size(); i++) {
            requiredOptionNames.add(requiredOptions.get(i).getName());
        }
        
        for (int i = 0; i < requiredOptionNames.size(); i++) {
            if (!givenOptions.contains(requiredOptionNames.get(i))) {
                throw new FaultException(-6, "requiredOptionMissing",
                        "Required advanced option missing: " + requiredOptionNames.get(i));
            }
        }
        
        Set<KickstartCommand> s = new HashSet<KickstartCommand>();
        
        for (Iterator itr = cmd.getAvailableOptions().iterator(); itr.hasNext();) {
            int index;
            KickstartCommandName cn = (KickstartCommandName) itr.next();
            
            index = -99;
            if (givenOptions.contains(cn.getName())) {
                for (int i = 0; i < options.size(); i++) {
                    if (cn.getName().equals(options.get(i).get("name"))) {
                        index = i;
                    }
                }
                
                KickstartCommand kc = new KickstartCommand();
                kc.setCommandName(cn);
                kc.setKickstartData(cmd.getKickstartData());
                kc.setCreated(new Date());
                kc.setModified(new Date());                        
                if (cn.getArgs().booleanValue()) {
                    String argsName = cn.getName() + "_txt";
                    // handle password encryption
                    if (cn.getName().equals("rootpw")) {
                        String pwarg = (String) options.get(index).get("arguments");
                        // password already encrypted
                        if (pwarg.startsWith("$1$")) {
                            kc.setArguments(pwarg);
                        }
                        // password changed, encrypt it 
                        else {
                            kc.setArguments(MD5Crypt.crypt(pwarg));
                        }
                    }
                    else {
                        kc.setArguments((String) options.get(index).get("arguments"));
                    }
                }
                s.add(kc);
            }                
        }
        cmd.getKickstartData().getOptions().clear();
        cmd.getKickstartData().getOptions().addAll(s);
           
        return 1;        
    }
    
    /**
     * Get custom options for a kickstart profile.
     * @param sessionKey the session key
     * @param ksLabel the kickstart label
     * @return a list of hashes holding this info.
     * @throws FaultException A FaultException is thrown if
     *         the profile associated with ksLabel cannot be found
     *
     * @xmlrpc.doc Get custom options for a kickstart profile.
     * @xmlrpc.param #session_key() 
     * @xmlrpc.param #param("string","ksLabel")
     *  
     * @xmlrpc.returntype
     * #array()
     * $KickstartCommandSerializer
     * #array_end()
     */
    public Object[] getCustomOptions(String sessionKey, String ksLabel) 
    throws FaultException {
        User user = getLoggedInUser(sessionKey);
        KickstartData ksdata = KickstartFactory.lookupKickstartDataByLabelAndOrgId(
                ksLabel, user.getOrg().getId());
        if (ksdata == null) {
            throw new FaultException(-3, "kickstartProfileNotFound", 
            "No Kickstart Profile found with label: " + ksLabel);
        }
        SortedSet options = ksdata.getCustomOptions();
        return options.toArray();
    }

   /**
    * Set custom options for a kickstart profile.
    * @param sessionKey the session key
    * @param ksLabel the kickstart label
    * @param options the custom options to set
    * @return a int being the number of options set
    * @throws FaultException A FaultException is thrown if
    *         the profile associated with ksLabel cannot be found
    *
    * @xmlrpc.doc Set custom options for a kickstart profile.
    * @xmlrpc.param #session_key()
    * @xmlrpc.param #param("string","ksLabel")
    * @xmlrpc.param #param("string[]","options")
    * @xmlrpc.returntype #return_int_success()
    */
   public int setCustomOptions(String sessionKey, String ksLabel, List<String> options) 
   throws FaultException {
       User user = getLoggedInUser(sessionKey);
       KickstartData ksdata = 
               XmlRpcKickstartHelper.getInstance().lookupKsData(ksLabel, user.getOrg());
       if (ksdata == null) {
           throw new FaultException(-3, "kickstartProfileNotFound",
               "No Kickstart Profile found with label: " + ksLabel);
       }
       Long ksid = ksdata.getId();
       KickstartHelper helper = new KickstartHelper(null);
       KickstartOptionsCommand cmd = new KickstartOptionsCommand(ksid, user, helper);
       Set customSet = new HashSet();
       if (options != null) {
           for (int i = 0; i < options.size(); i++) {
               String option = options.get(i);
               KickstartCommand custom = new KickstartCommand();
               custom.setCommandName(
                    KickstartFactory.lookupKickstartCommandName("custom"));
               
               // the following is a workaround to ensure that the options are rendered
               // on the UI on separate lines.
               if (i < (options.size() - 1)) {
                   option += "\r";
               } 
                   
               custom.setArguments(option);
               custom.setKickstartData(cmd.getKickstartData());
               custom.setCustomPosition(customSet.size());
               custom.setCreated(new Date());
               custom.setModified(new Date());
               customSet.add(custom);
           }
           cmd.getKickstartData().getCustomOptions().clear();
           cmd.getKickstartData().getCustomOptions().addAll(customSet);
           cmd.store();
       }
       return 1;
   }

   /**
    * Lists all ip ranges for a kickstart profile.
    * @param sessionKey An active session key
    * @param ksLabel the label of the kickstart
    * @return List of KickstartIpRange objects
    * 
    * @xmlrpc.doc List all ip ranges for a kickstart profile.
    * @xmlrpc.param #session_key()
    * @xmlrpc.param #param_desc("string", "label", "The label of the
    * kickstart")
    * @xmlrpc.returntype #array() $KickstartIpRangeSerializer #array_end()
    * 
    */
   public Set listIpRanges(String sessionKey, String ksLabel) {
       User user = getLoggedInUser(sessionKey);
       if (!user.hasRole(RoleFactory.CONFIG_ADMIN)) {
           throw new PermissionCheckFailureException();
       }
       KickstartData ksdata = lookupKsData(ksLabel, user.getOrg());
       return ksdata.getIps();
   }

   /**
    * Add an ip range to a kickstart.
    * @param sessionKey the session key
    * @param ksLabel the kickstart label
    * @param min the min ip address of the range
    * @param max the max ip address of the range
    * @return 1 on success
    * 
    * @xmlrpc.doc Add an ip range to a kickstart profile.
    * @xmlrpc.param #session_key()
    * @xmlrpc.param #param_desc("string", "label", "The label of the
    * kickstart")
    * @xmlrpc.param #param_desc("string", "min", "The ip address making up the
    * minimum of the range (i.e. 192.168.0.1)")
    * @xmlrpc.param #param_desc("string", "max", "The ip address making up the
    * maximum of the range (i.e. 192.168.0.254)")
    * @xmlrpc.returntype #return_int_success()
    * 
    */
   public int addIpRange(String sessionKey, String ksLabel, String min,
           String max) {
       User user = getLoggedInUser(sessionKey);
       KickstartData ksdata = lookupKsData(ksLabel, user.getOrg());
       KickstartIpCommand com = new KickstartIpCommand(ksdata.getId(), user);

       IpAddress minIp = new IpAddress(min);
       IpAddress maxIp = new IpAddress(max);

       if (!com.addIpRange(minIp.getOctets(), maxIp.getOctets())) {
           throw new IpRangeConflictException(min + " - " + max);
       }
       com.store();
       return 1;
   }

   /**
    * Remove an ip range from a kickstart profile.
    * @param sessionKey the session key
    * @param ksLabel the kickstart to remove an ip range from
    * @param ipAddress an ip address in the range that you want to remove
    * @return 1 on removal, 0 if not found, exception otherwise
    * 
    * @xmlrpc.doc Remove an ip range from a kickstart profile.
    * @xmlrpc.param #session_key()
    * @xmlrpc.param #param_desc("string", "ksLabel", "The kickstart label of
    * the ip range you want to remove")
    * @xmlrpc.param #param_desc("string", "ip_address", "An Ip Address that
    * falls within the range that you are wanting to remove. The min or max of
    * the range will work.")
    * @xmlrpc.returntype int - 1 on successful removal, 0 if range wasn't found
    * for the specified kickstart, exception otherwise.
    */
   public int removeIpRange(String sessionKey, String ksLabel, String ipAddress) {
       User user = getLoggedInUser(sessionKey);
       if (!user.hasRole(RoleFactory.CONFIG_ADMIN)) {
           throw new PermissionCheckFailureException();
       }
       KickstartData ksdata = lookupKsData(ksLabel, user.getOrg());
       KickstartIpRangeFilter filter = new KickstartIpRangeFilter();
       for (KickstartIpRange range : ksdata.getIps()) {
           if (filter.filterOnRange(ipAddress, range.getMinString(), range
                   .getMaxString())) {
               ksdata.getIps().remove(range);
               return 1;
           }
       }
       return 0;
   }
   
    /**
     * Returns a list for each kickstart profile of activation keys that are present
     * in that profile but not the other.
     * 
     * @param sessionKey      identifies the user making the call; 
     *                        cannot be <code>null</code> 
     * @param kickstartLabel1 identifies a profile to be compared;
     *                        cannot be <code>null</code>  
     * @param kickstartLabel2 identifies a profile to be compared;
     *                        cannot be <code>null</code>
     *  
     * @return map of kickstart label to a list of keys in that profile but not in
     *         the other; if no keys match the criteria the list will be empty
     *
     * @xmlrpc.doc Returns a list for each kickstart profile; each list will contain
     *             activation keys not present on the other profile.
     * @xmlrpc.param #param("string", "sessionKey") 
     * @xmlrpc.param #param("string", "kickstartLabel1") 
     * @xmlrpc.param #param("string", "kickstartLabel2") 
     * @xmlrpc.returntype 
     *  #struct("Comparison Info")
     *      #prop_desc("array", "kickstartLabel1", "Actual label of the first kickstart
     *                 profile is the key into the struct")
     *          #array() 
     *              $ActivationKeySerializer
     *          #array_end()
     *      #prop_desc("array", "kickstartLabel2", "Actual label of the second kickstart
     *                 profile is the key into the struct")
     *          #array() 
     *              $ActivationKeySerializer
     *          #array_end()
     *  #struct_end()
     */
    public Map<String, List<ActivationKey>> compareActivationKeys(String sessionKey,
                                                                  String kickstartLabel1,
                                                                  String kickstartLabel2) {
        // Validate parameters
        if (sessionKey == null) {
            throw new IllegalArgumentException("sessionKey cannot be null");
        }
    
        if (kickstartLabel1 == null) {
            throw new IllegalArgumentException("kickstartLabel1 cannot be null");
        }
    
        if (kickstartLabel2 == null) {
            throw new IllegalArgumentException("kickstartLabel2 cannot be null");
        }
        
        // Leverage exisitng handler for key loading
        KeysHandler keysHandler = new KeysHandler();
    
        List<ActivationKey> keyList1 =
            keysHandler.getActivationKeys(sessionKey, kickstartLabel1);
        List<ActivationKey> keyList2 = 
            keysHandler.getActivationKeys(sessionKey, kickstartLabel2);
        
        // Set operations to determine deltas
        List<ActivationKey> onlyInKickstart1 = new ArrayList<ActivationKey>(keyList1);
        onlyInKickstart1.removeAll(keyList2);
        
        List<ActivationKey> onlyInKickstart2 = new ArrayList<ActivationKey>(keyList2);
        onlyInKickstart2.removeAll(keyList1);
        
        // Package up for return
        Map<String, List<ActivationKey>> results =
            new HashMap<String, List<ActivationKey>>(2);
        
        results.put(kickstartLabel1, onlyInKickstart1);
        results.put(kickstartLabel2, onlyInKickstart2);
        
        return results;
    }
    
    /**
     * Returns a list for each kickstart profile of package names that are present
     * in that profile but not the other.
     * 
     * @param sessionKey      identifies the user making the call; 
     *                        cannot be <code>null</code> 
     * @param kickstartLabel1 identifies a profile to be compared;
     *                        cannot be <code>null</code>  
     * @param kickstartLabel2 identifies a profile to be compared;
     *                        cannot be <code>null</code>
     *  
     * @return map of kickstart label to a list of package names in that profile but not in
     *         the other; if no keys match the criteria the list will be empty
     *
     * @xmlrpc.doc Returns a list for each kickstart profile; each list will contain
     *             package names not present on the other profile.
     * @xmlrpc.param #param("string", "sessionKey") 
     * @xmlrpc.param #param("string", "kickstartLabel1") 
     * @xmlrpc.param #param("string", "kickstartLabel2") 
     * @xmlrpc.returntype 
     *  #struct("Comparison Info")
     *      #prop_desc("array", "kickstartLabel1", "Actual label of the first kickstart
     *                 profile is the key into the struct")
     *          #array() 
     *              #prop("string", "package name")
     *          #array_end()
     *      #prop_desc("array", "kickstartLabel2", "Actual label of the second kickstart
     *                 profile is the key into the struct")
     *          #array() 
     *              #prop("string", "package name")
     *          #array_end()
     *  #struct_end()
     */
    public Map<String, Set<String>> comparePackages(String sessionKey,
                                       String kickstartLabel1, String kickstartLabel2) {
        // Validate parameters
        if (sessionKey == null) {
            throw new IllegalArgumentException("sessionKey cannot be null");
        }
    
        if (kickstartLabel1 == null) {
            throw new IllegalArgumentException("kickstartLabel1 cannot be null");
        }
    
        if (kickstartLabel2 == null) {
            throw new IllegalArgumentException("kickstartLabel2 cannot be null");
        }
        
        // Load the profiles and their package lists
        User loggedInUser = getLoggedInUser(sessionKey);
        KickstartData profile1 =
            KickstartFactory.lookupKickstartDataByLabelAndOrgId(kickstartLabel1,
                loggedInUser.getOrg().getId());
        
        KickstartData profile2 =
            KickstartFactory.lookupKickstartDataByLabelAndOrgId(kickstartLabel2,
                loggedInUser.getOrg().getId());
        
        // Set operations to determine deltas
        Set<PackageName> onlyInProfile1 =
            new HashSet<PackageName>(profile1.getPackageNames());
        onlyInProfile1.removeAll(profile2.getPackageNames());
        
        Set<PackageName> onlyInProfile2 = 
            new HashSet<PackageName>(profile2.getPackageNames());
        onlyInProfile2.removeAll(profile1.getPackageNames());
        
        // Convert the remaining into strings for return
        Set<String> profile1PackageNameStrings = new HashSet<String>(onlyInProfile1.size());
        for (PackageName packageName : onlyInProfile1) {
            profile1PackageNameStrings.add(packageName.getName());
        }
        
        Set<String> profile2PackageNameStrings = new HashSet<String>(onlyInProfile2.size());
        for (PackageName packageName : onlyInProfile2) {
            profile2PackageNameStrings.add(packageName.getName());
        }
    
        // Package for return
        Map<String, Set<String>> results = new HashMap<String, Set<String>>(2);
        
        results.put(kickstartLabel1, profile1PackageNameStrings);
        results.put(kickstartLabel2, profile2PackageNameStrings);
        
        return results;
    }
    
    /**
     * Returns a list for each kickstart profile of properties that are different between
     * the profiles. Each property that is not equal between the two profiles will be
     * present in both lists with the current values for its respective profile. 
     * 
     * @param sessionKey      identifies the user making the call; 
     *                        cannot be <code>null</code> 
     * @param kickstartLabel1 identifies a profile to be compared;
     *                        cannot be <code>null</code>  
     * @param kickstartLabel2 identifies a profile to be compared;
     *                        cannot be <code>null</code>
     * 
     * @return map of kickstart label to a list of properties and their values whose
     *         values are different for each profile
     * 
     * @xmlrpc.doc Returns a list for each kickstart profile; each list will contain the
     *             properties that differ between the profiles and their values for that
     *             specific profile .
     * @xmlrpc.param #param("string", "sessionKey") 
     * @xmlrpc.param #param("string", "kickstartLabel1") 
     * @xmlrpc.param #param("string", "kickstartLabel2") 
     * @xmlrpc.returntype 
     *  #struct("Comparison Info")
     *      #prop_desc("array", "kickstartLabel1", "Actual label of the first kickstart
     *                 profile is the key into the struct")
     *          #array() 
     *              $KickstartOptionValueSerializer              
     *          #array_end()
     *      #prop_desc("array", "kickstartLabel2", "Actual label of the second kickstart
     *                 profile is the key into the struct")
     *          #array() 
     *              $KickstartOptionValueSerializer
     *          #array_end()
     *  #struct_end()
     */
    public Map<String, List<KickstartOptionValue>> compareAdvancedOptions(String sessionKey,
                                        String kickstartLabel1, String kickstartLabel2) {
        // Validate parameters
        if (sessionKey == null) {
            throw new IllegalArgumentException("sessionKey cannot be null");
        }
    
        if (kickstartLabel1 == null) {
            throw new IllegalArgumentException("kickstartLabel1 cannot be null");
        }
        
        if (kickstartLabel2 == null) {
            throw new IllegalArgumentException("kickstartLabel2 cannot be null");
        }
    
        // Load the profiles
        User loggedInUser = getLoggedInUser(sessionKey);
        KickstartData profile1 =
            KickstartFactory.lookupKickstartDataByLabelAndOrgId(kickstartLabel1,
                loggedInUser.getOrg().getId());
        
        KickstartData profile2 =
            KickstartFactory.lookupKickstartDataByLabelAndOrgId(kickstartLabel2,
                loggedInUser.getOrg().getId());
        
        // Load the options
        KickstartOptionsCommand profile1OptionsCommand =
            new KickstartOptionsCommand(profile1.getId(), loggedInUser);
    
        KickstartOptionsCommand profile2OptionsCommand =
            new KickstartOptionsCommand(profile2.getId(), loggedInUser);
    
        // Set operations to determine which values are different. The equals method
        // of KickstartOptionValue will take the name and value into account, so
        // only cases where this tuple is present in both will be removed.
        List<KickstartOptionValue> onlyInProfile1 =
            profile1OptionsCommand.getDisplayOptions();
        onlyInProfile1.removeAll(profile2OptionsCommand.getDisplayOptions());
        
        List<KickstartOptionValue> onlyInProfile2 =
            profile2OptionsCommand.getDisplayOptions();
        onlyInProfile2.removeAll(profile1OptionsCommand.getDisplayOptions());
        
        // Package for transport
        Map<String, List<KickstartOptionValue>> results =
            new HashMap<String, List<KickstartOptionValue>>(2);
        results.put(kickstartLabel1, onlyInProfile1);
        results.put(kickstartLabel2, onlyInProfile2);
        
        return results;
    }
    
    private void checkKickstartPerms(User user) {
        if (!user.hasRole(RoleFactory.CONFIG_ADMIN)) {
            throw new PermissionException(LocalizationService.getInstance()
                    .getMessage("permission.configadmin.needed"));
        }
    }
    
    private KickstartData lookupKsData(String label, Org org) {
        return XmlRpcKickstartHelper.getInstance().lookupKsData(label, org);
    }
}
