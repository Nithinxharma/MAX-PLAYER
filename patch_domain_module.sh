sed -i 's/RepositoryManager(get(), get())/RepositoryManager(androidContext(), get(), get())/g' app/src/main/kotlin/xyz/mpv/rex/di/DomainModule.kt
sed -i 's/ExtensionManager(androidContext(), get(), get(), get(), get())/ExtensionManager(androidContext(), get(), get(), get())/g' app/src/main/kotlin/xyz/mpv/rex/di/DomainModule.kt
