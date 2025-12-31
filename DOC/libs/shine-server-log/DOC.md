shine-log (BlockchainAdminNotifier)

Суть (1 предложение): единая точка для “красного” оповещения админа о критических проблемах консистентности, сейчас — через максимально заметный log.error, позже — через Telegram/email/webhook и т.п.

Структура (очень кратко):

BlockchainAdminNotifier (final utility)

BlockchainAdminNotifier.critical(String message)
BlockchainAdminNotifier.critical(String message, Throwable t)