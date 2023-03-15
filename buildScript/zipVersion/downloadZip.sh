set -e

# $1 is work dir
# $2 is name list file
# $3 is download URL list file

NAMELIST=$(realpath $2)
URLLIST=$(realpath $3)

cd $1

count=`wc -l $NAMELIST|cut -d " " -f 1`

# download all
for ((i=1; i<=$(($count)); i++))
do
    name=$(sed -n $i'p' $NAMELIST)
    url=$(sed -n $i'p' $URLLIST)
    
    curl -L -o "$name".zip "$url" > /dev/null 2>&1
    unzip "$name".zip > /dev/null 2>&1
    rm "$name".zip
    mv "$name"-* "$name"
done
