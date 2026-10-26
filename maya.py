import os
name = "Tilak Verma"
text = "Namaste, main aapki assistant Maya hoon. Aapka naam " + name + " hai."
print(text)
os.system("termux-tts-speak \"" + text + "\"")
