import pandas as pd

files = [
    "datasets/lhfa_boys_2-to-5-years_zscores.xlsx",
    "datasets/lhfa_girls_2-to-5-years_zscores.xlsx",
    "datasets/bmifa-girls-5-19years-z.xlsx",
    "datasets/bmifa-boys-5-19years-z.xlsx",
    "datasets/sft-hfa-girls-z-5-19years.xlsx",
    "datasets/sft-hfa-boys-z-5-19years.xlsx"
]

for f in files:
    print("\n====================")
    print(f)
    df = pd.read_excel(f)
    print(df.head())
    print(df.columns.tolist())