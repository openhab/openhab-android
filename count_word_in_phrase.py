phrase = str(input('Enter a phrase: '))
word = str(input('enter the word you want to count: '))

def count_word_in_phrase(phrase, word):
    words = phrase.lower().split()
    count = 0
    for wd in words:
        if wd == word.lower(): count += 1
    return count

print(count_word_in_phrase(phrase, word))
