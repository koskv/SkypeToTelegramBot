package pro.zackpollard.telegrambot.skypetotelegrambot.telegram.listeners;

import com.samczsun.skype4j.Skype;
import com.samczsun.skype4j.chat.Chat;
import com.samczsun.skype4j.chat.GroupChat;
import com.samczsun.skype4j.chat.IndividualChat;
import com.samczsun.skype4j.exceptions.ChatNotFoundException;
import com.samczsun.skype4j.exceptions.ConnectionException;
import pro.zackpollard.telegrambot.api.TelegramBot;
import pro.zackpollard.telegrambot.api.chat.ChatType;
import pro.zackpollard.telegrambot.api.chat.message.send.SendableTextMessage;
import pro.zackpollard.telegrambot.api.event.Listener;
import pro.zackpollard.telegrambot.api.event.chat.message.CommandMessageReceivedEvent;
import pro.zackpollard.telegrambot.api.keyboards.KeyboardButton;
import pro.zackpollard.telegrambot.api.keyboards.ReplyKeyboardMarkup;
import pro.zackpollard.telegrambot.skypetotelegrambot.SkypeToTelegramBot;
import pro.zackpollard.telegrambot.skypetotelegrambot.storage.PermissionsStore;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Zack Pollard
 */
public class TelegramCommandListener implements Listener {

    private final SkypeToTelegramBot instance;
    private final TelegramBot telegramBot;

    public TelegramCommandListener(SkypeToTelegramBot instance) {

        this.instance = instance;
        this.telegramBot = instance.getTelegramBot();
    }

    @Override
    public void onCommandMessageReceived(CommandMessageReceivedEvent event) {

        switch(event.getCommand().toLowerCase()) {

            case "login": {

                if(instance.getSkypeManager().getPermissionsStore().getUserRoles().isEmpty()) {

                    instance.getSkypeManager().getPermissionsStore().setRole(event.getMessage().getSender().getId(), PermissionsStore.UserRole.SUPERUSER);
                    event.getChat().sendMessage("You were set as a superuser for the bot.");
                    instance.saveSkypeManager();
                }

                if(event.getChat().getType().equals(ChatType.PRIVATE)) {

                    if(event.getArgs().length == 2) {

                        boolean success = instance.getSkypeManager().addUser(event.getMessage().getSender(), event.getArgs()[0], event.getArgs()[1]);
                        if(success) event.getChat().sendMessage("Successfully authorised with skype.");
                    } else {

                        event.getChat().sendMessage("Correct usage is: /login [username] [password]");
                    }
                }

                break;
            }

            case "link": {

                if(event.getChat().getType().equals(ChatType.GROUP)) {

                    if(!instance.getSkypeManager().isLinked(event.getChat())) {

                        Skype skype = instance.getSkypeManager().getSkype(event.getMessage().getSender());

                        if(skype != null) {

                            if(event.getArgs().length == 0) {

                                Map<String, String> chats = new HashMap<>();

                                for (Chat chat : skype.getAllChats()) {

                                    if (chat instanceof GroupChat) {

                                        chats.put(((GroupChat) chat).getTopic(), chat.getIdentity());
                                    } else if (chat instanceof IndividualChat) {

                                        chats.put(chat.getIdentity().substring(2), chat.getIdentity());
                                    }
                                }

                                ReplyKeyboardMarkup.ReplyKeyboardMarkupBuilder keyboardMarkupBuilder = ReplyKeyboardMarkup.builder().resize(true).oneTime(true).selective(true);

                                for(String chat : chats.keySet()) {
                                    keyboardMarkupBuilder.addRow(KeyboardButton.builder().text(chat).build());
                                }

                                telegramBot.sendMessage(event.getChat(), SendableTextMessage.builder().message("Please select the chat you want to link. You can also do /link (username) and /link (chatID). You can get the chat ID of a skype chat by typing /showname in the skype chat you would like to link.").replyMarkup(keyboardMarkupBuilder.build()).replyTo(event.getMessage()).build());

                                instance.getSkypeManager().getLinkingQueue().put(event.getChat().getId(), chats);
                            } else if(event.getArgs().length == 1) {

                                String chatID = event.getArgs()[0];

                                if(!Character.isDigit(chatID.charAt(0))) {

                                    chatID = "8:" + chatID;
                                }

                                Chat chat = skype.getChat(chatID);

                                if(chat == null) {

                                    try {
                                        chat = skype.loadChat(chatID);
                                    } catch (ConnectionException e) {
                                        telegramBot.sendMessage(event.getChat(), SendableTextMessage.builder().message("There was a connection error whilst trying to link the chat, please try again later.").replyTo(event.getMessage()).build());
                                    } catch (ChatNotFoundException e) {
                                        telegramBot.sendMessage(event.getChat(), SendableTextMessage.builder().message("A chat with the ID you entered does not exist.").replyTo(event.getMessage()).build());
                                    }
                                }

                                if(chat != null) {

                                    instance.getSkypeManager().createLink(event.getMessage().getSender().getId(), (pro.zackpollard.telegrambot.api.chat.GroupChat) event.getChat(), chat);
                                } else {

                                    telegramBot.sendMessage(event.getChat(), SendableTextMessage.builder().message("There was an error whilst trying to link the chat, please report this to @zackpollard.").replyTo(event.getMessage()).build());
                                }
                            }
                        } else {

                            telegramBot.sendMessage(event.getChat(), SendableTextMessage.builder().message("You must authenticate with the bot first by typing /login (username) (password) in the bots private chat.").replyTo(event.getMessage()).build());
                        }
                    } else {

                        telegramBot.sendMessage(event.getChat(), SendableTextMessage.builder().message("This chat is already linked, either create another chat to make a new link, or /unlink this chat.").replyTo(event.getMessage()).build());
                    }
                }

                break;
            }

            case "logout": {

                if(event.getChat().getType().equals(ChatType.PRIVATE)) {

                    if(instance.getSkypeManager().removeUser(((pro.zackpollard.telegrambot.api.chat.IndividualChat) event.getChat()).getPartner())) {

                        event.getChat().sendMessage("You have been logged out successfully.");
                    } else {

                        event.getChat().sendMessage("You weren't logged in.");
                    }
                } else {

                    event.getChat().sendMessage("This command can only be used in a private chat.");
                }

                break;
            }

            case "unlink": {

                if(event.getChat().getType().equals(ChatType.GROUP)) {

                    if(instance.getSkypeManager().removeLink(event.getChat(), event.getMessage().getSender().getId())) {

                        event.getChat().sendMessage("The link to this chat was successfully removed.");
                    } else {

                        event.getChat().sendMessage("This chat is not linked to a skype chat.");
                    }
                }

                break;
            }

            case "shutdown": {

                if(event.getChat().getType().equals(ChatType.PRIVATE)) {

                    if(instance.getSkypeManager().getPermissionsStore().getRole(event.getMessage().getSender().getId()).equals(PermissionsStore.UserRole.SUPERUSER)) {

                        System.out.println("User: " + event.getMessage().getSender().getId() + " (" + event.getMessage().getSender().getUsername() + ") told the bot to shutdown.");
                        instance.saveSkypeManager();
                        System.exit(0);
                    } else {

                        event.getChat().sendMessage(SendableTextMessage.builder().message("You do not have permission to do this.").replyTo(event.getMessage()).build());
                    }
                }

                break;
            }

            case "useasprivate": {

                if(event.getChat().getType().equals(ChatType.GROUP)) {

                    if(instance.getSkypeManager().getPrivateMessageGroups().containsKey(event.getMessage().getSender().getId())) {

                        event.getChat().sendMessage(SendableTextMessage.builder().message("Another chat was set as the private message chat, but has been unlinked now.").replyTo(event.getMessage()).build());
                        telegramBot.getChat(instance.getSkypeManager().getPrivateMessageGroups().get(event.getMessage().getSender().getId())).sendMessage("This chat has been unlinked due to another chat being setup for private messages.");
                    }

                    event.getChat().sendMessage(SendableTextMessage.builder().message("This group will now receive all of the private messages that aren't set to go to specific groups.").replyTo(event.getMessage()).build());
                    instance.getSkypeManager().getPrivateMessageGroups().put(event.getMessage().getSender().getId(), event.getChat().getId());
                }

                break;
            }

            case "setimgurid": {

                if(event.getChat().getType().equals(ChatType.PRIVATE)) {

                    if(instance.getSkypeManager().getPermissionsStore().getRole(event.getMessage().getSender().getId()).equals(PermissionsStore.UserRole.SUPERUSER)) {

                        System.out.println("User: " + event.getMessage().getSender().getId() + " (" + event.getMessage().getSender().getUsername() + ") set their imgur ID to " + event.getArgs()[0]);
                        instance.getSkypeManager().setImgurID(event.getMessage().getSender().getId(), event.getArgs()[0]);
                        instance.saveSkypeManager();
                    } else {

                        event.getChat().sendMessage(SendableTextMessage.builder().message("You do not have permission to do this.").replyTo(event.getMessage()).build());
                    }
                }

                break;
            }
        }
    }
}